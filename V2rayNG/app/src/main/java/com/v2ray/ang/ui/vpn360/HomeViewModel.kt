package com.v2ray.ang.ui.vpn360

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.handler.Vpn360Policy
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VpnState { Disconnected, Connecting, Connected }

/** Language change: the overlay covers the screen, the language is applied, then it reveals. */
data class LangSwitch(val code: String, val label: String, val greeting: String, val applied: Boolean = false)

data class HomeUiState(
    val vpnState: VpnState = VpnState.Disconnected,
    val hasAccount: Boolean = false,
    val serverName: String = "",
    val connectedSince: Long = 0L,
    val importing: Boolean = false,
    val codeDialogOpen: Boolean = false,
    val codeBusy: Boolean = false,
    val codeError: Int? = null,
    val expired: Boolean = false,
    val updateVersion: String? = null,
    val language: String = "auto",
    val langSwitch: LangSwitch? = null
)

/**
 * State of the simple VPN 360 home: account (any server imported), connection state reported by
 * the VPN service, access-code activation, plan expiry, app updates and language changes.
 */
class HomeViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        private const val PREF_CONNECTED_SINCE = "vpn360_connected_since"
        private const val PREF_UPDATE_CHECKED_AT = "vpn360_update_checked_at"
        private const val PREF_UPDATE_VERSION = "vpn360_update_version"
        private const val CONNECT_TIMEOUT_MS = 25_000L
        private const val UPDATE_EVERY_MS = 12 * 3600_000L
        private const val STATUS_EVERY_MS = 15 * 60_000L
        private const val PENDING_MAX_AGE_MS = 3 * 24 * 3600_000L

        /** a > b for versions like 1.0.10 */
        fun newerThan(a: String, b: String): Boolean {
            val x = a.split('.').map { it.toIntOrNull() ?: 0 }
            val y = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var connectTimeout: Job? = null
    private var lastStatusCheck = 0L

    init {
        val saved = MmkvManager.decodeSettingsString(PREF_UPDATE_VERSION).orEmpty()
        _uiState.update {
            it.copy(
                updateVersion = saved.takeIf { v -> v.isNotEmpty() && newerThan(v, BuildConfig.VERSION_NAME) },
                language = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE) ?: "auto"
            )
        }
    }

    /** Reloads the account / selected server and runs the periodic checks. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val servers = MmkvManager.decodeAllServerList()
            var selected = MmkvManager.getSelectServer()
            if (servers.isNotEmpty() && (selected.isNullOrEmpty() || selected !in servers)) {
                selected = servers.first()
                MmkvManager.setSelectServer(selected)
            }
            val name = selected?.let { MmkvManager.decodeServerConfig(it)?.remarks }.orEmpty()
            _uiState.update {
                it.copy(
                    hasAccount = servers.isNotEmpty(),
                    serverName = name,
                    language = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE) ?: "auto"
                )
            }
            checkPendingOrder()
            checkAccountStatus(force = false)
            checkUpdateIfDue()
        }
    }

    // ---------------- VPN state ----------------

    /** State broadcast by the VPN service (AppConfig.MSG_STATE_*). */
    fun onServiceMessage(key: Int) {
        when (key) {
            AppConfig.MSG_STATE_RUNNING -> setConnected(fresh = false)
            AppConfig.MSG_STATE_START_SUCCESS -> setConnected(fresh = true)
            AppConfig.MSG_STATE_START_FAILURE -> {
                setDisconnected()
                toastError(R.string.vpn360_error_start)
            }

            AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_STOP_SUCCESS -> setDisconnected()
        }
    }

    /** Called right before the service is asked to start. */
    fun onConnectRequested() {
        _uiState.update { it.copy(vpnState = VpnState.Connecting) }
        connectTimeout?.cancel()
        connectTimeout = viewModelScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_uiState.value.vpnState == VpnState.Connecting) {
                _uiState.update { it.copy(vpnState = VpnState.Disconnected) }
                toastError(R.string.vpn360_error_start)
            }
        }
    }

    fun onConnectCancelled() {
        connectTimeout?.cancel()
        _uiState.update { it.copy(vpnState = VpnState.Disconnected) }
    }

    private fun setConnected(fresh: Boolean) {
        connectTimeout?.cancel()
        var since = MmkvManager.decodeSettingsLong(PREF_CONNECTED_SINCE, 0L)
        if (fresh || since <= 0L) {
            since = System.currentTimeMillis()
            MmkvManager.encodeSettings(PREF_CONNECTED_SINCE, since)
        }
        _uiState.update { it.copy(vpnState = VpnState.Connected, connectedSince = since) }
    }

    private fun setDisconnected() {
        connectTimeout?.cancel()
        MmkvManager.encodeSettings(PREF_CONNECTED_SINCE, 0L)
        _uiState.update { it.copy(vpnState = VpnState.Disconnected, connectedSince = 0L) }
    }

    // ---------------- activation ----------------

    /**
     * Handles what the QR scanner read: a vless:// link, an https subscription link, a
     * vpn360://install-sub link, or a plain access code.
     */
    fun onScanned(text: String) {
        val value = text.trim()
        when {
            value.startsWith("vpn360://", ignoreCase = true) -> {
                val url = Uri.parse(value).getQueryParameter("url")
                if (url.isNullOrBlank()) toastError(R.string.vpn360_not_ours) else importConfig(url)
            }

            value.contains("://") -> importConfig(value)
            else -> activateCode(value)
        }
    }

    /** Imports a VPN 360 link (our vless server or our subscription) and selects its first server. */
    fun importConfig(text: String) {
        if (_uiState.value.importing) return
        if (Vpn360Policy.filter(text) == null) {
            toastError(R.string.vpn360_not_ours)
            return
        }
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val (count, countSub) = AngConfigManager.importBatchConfig(text, "", true)
                var imported = count + countSub > 0
                if (!imported && Vpn360Policy.isOurSubscription(text.trim()) &&
                    MmkvManager.decodeSubscriptions().any { it.subscription.url == text.trim() }
                ) {
                    // Same account scanned again: just refresh it
                    AngConfigManager.updateConfigViaSubAll()
                    imported = true
                }
                imported && MmkvManager.decodeAllServerList().isNotEmpty()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "VPN 360 import failed", e)
                false
            }
            if (ok) {
                MmkvManager.decodeAllServerList().firstOrNull()?.let { MmkvManager.setSelectServer(it) }
                MmkvManager.encodeSettings(WelcomeActivity.PREF_ONBOARDED, true)
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(importing = false, expired = if (ok) false else it.expired) }
                refresh()
                if (ok) toastSuccess(R.string.vpn360_account_activated) else toastError(R.string.vpn360_invalid_code)
            }
        }
    }

    fun openCodeDialog() = _uiState.update { it.copy(codeDialogOpen = true, codeError = null) }

    fun closeCodeDialog() = _uiState.update { it.copy(codeDialogOpen = false, codeBusy = false, codeError = null) }

    /** Exchanges an access code for the subscription link at the VPN 360 account server. */
    fun activateCode(rawCode: String) {
        val code = rawCode.uppercase().filter { it.isLetterOrDigit() }
        if (code.length < 6) {
            _uiState.update { it.copy(codeDialogOpen = true, codeError = R.string.vpn360_error_wrong_code) }
            return
        }
        if (!Vpn360Api.enabled) {
            _uiState.update { it.copy(codeDialogOpen = true, codeError = R.string.vpn360_error_soon) }
            return
        }
        _uiState.update { it.copy(codeDialogOpen = true, codeBusy = true, codeError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = Vpn360Api.login(code)
            withContext(Dispatchers.Main) {
                if (result.subUrl != null) {
                    _uiState.update { it.copy(codeDialogOpen = false, codeBusy = false, codeError = null) }
                    importConfig(result.subUrl)
                } else {
                    _uiState.update { it.copy(codeBusy = false, codeError = result.error) }
                }
            }
        }
    }

    // ---------------- purchase, expiry, updates ----------------

    /** A purchase started in the app whose confirmation arrived while the app was closed. */
    private suspend fun checkPendingOrder() {
        val ref = MmkvManager.decodeSettingsString(Vpn360Api.PREF_PENDING_REF).orEmpty()
        if (ref.isEmpty() || !Vpn360Api.enabled) return
        val at = MmkvManager.decodeSettingsLong(Vpn360Api.PREF_PENDING_AT, 0L)
        if (System.currentTimeMillis() - at > PENDING_MAX_AGE_MS) {
            clearPending()
            return
        }
        val order = Vpn360Api.order(ref)
        when {
            !order.known -> clearPending()
            order.active && order.subUrl != null -> {
                clearPending()
                withContext(Dispatchers.Main) { importConfig(order.subUrl) }
            }
        }
    }

    private fun clearPending() {
        MmkvManager.encodeSettings(Vpn360Api.PREF_PENDING_REF, "")
        MmkvManager.encodeSettings(Vpn360Api.PREF_PENDING_AT, 0L)
    }

    /** Shows "Your plan has expired — Renew" when the account server says the plan is not active. */
    private fun checkAccountStatus(force: Boolean) {
        if (!Vpn360Api.enabled || !_uiState.value.hasAccount) return
        val now = System.currentTimeMillis()
        if (!force && now - lastStatusCheck < STATUS_EVERY_MS) return
        lastStatusCheck = now
        val active = Vpn360Api.isAccountActive() ?: return
        _uiState.update { it.copy(expired = !active) }
    }

    /** Direct-download builds check GitHub for a newer release at most twice a day. */
    private suspend fun checkUpdateIfDue() {
        if (BuildConfig.DISTRIBUTION != "Web") return
        val now = System.currentTimeMillis()
        if (now - MmkvManager.decodeSettingsLong(PREF_UPDATE_CHECKED_AT, 0L) < UPDATE_EVERY_MS) return
        MmkvManager.encodeSettings(PREF_UPDATE_CHECKED_AT, now)
        try {
            val result = UpdateCheckerManager.checkForUpdate(false)
            val version = if (result.hasUpdate) result.latestVersion.orEmpty() else ""
            MmkvManager.encodeSettings(PREF_UPDATE_VERSION, version)
            _uiState.update { it.copy(updateVersion = version.takeIf { v -> v.isNotEmpty() }) }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "VPN 360 update check failed", e)
        }
    }

    // ---------------- language ----------------

    fun startLanguageSwitch(code: String, label: String, greeting: String) {
        if (_uiState.value.langSwitch != null) return
        _uiState.update { it.copy(langSwitch = LangSwitch(code, label, greeting)) }
    }

    /** Called by the activity right after asking AppCompat to apply the language. */
    fun languageApplied() {
        _uiState.update { s -> s.copy(langSwitch = s.langSwitch?.copy(applied = true)) }
    }

    fun finishLanguageSwitch() {
        _uiState.update {
            it.copy(langSwitch = null, language = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE) ?: "auto")
        }
    }
}
