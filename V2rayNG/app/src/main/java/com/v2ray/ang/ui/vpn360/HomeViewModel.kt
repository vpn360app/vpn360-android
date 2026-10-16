package com.v2ray.ang.ui.vpn360

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class VpnState { Disconnected, Connecting, Connected }

data class HomeUiState(
    val vpnState: VpnState = VpnState.Disconnected,
    val hasAccount: Boolean = false,
    val serverName: String = "",
    val connectedSince: Long = 0L,
    val importing: Boolean = false,
    val codeDialogOpen: Boolean = false,
    val codeBusy: Boolean = false,
    val codeError: Int? = null
)

/**
 * State of the simple VPN 360 home: is there an account (any server imported), the connection
 * state reported by the VPN service, and the access-code activation.
 */
class HomeViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        private const val PREF_CONNECTED_SINCE = "vpn360_connected_since"
        private const val CONNECT_TIMEOUT_MS = 25_000L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var connectTimeout: Job? = null

    /** Reloads the account / selected server (e.g. after returning from advanced mode). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val servers = MmkvManager.decodeAllServerList()
            var selected = MmkvManager.getSelectServer()
            if (servers.isNotEmpty() && (selected.isNullOrEmpty() || selected !in servers)) {
                selected = servers.first()
                MmkvManager.setSelectServer(selected)
            }
            val name = selected?.let { MmkvManager.decodeServerConfig(it)?.remarks }.orEmpty()
            _uiState.update { it.copy(hasAccount = servers.isNotEmpty(), serverName = name) }
        }
    }

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

    /**
     * Handles what the QR scanner read: a vless:// link, an https subscription link, a
     * vpn360://install-sub link, or a plain access code.
     */
    fun onScanned(text: String) {
        val value = text.trim()
        when {
            value.startsWith("vpn360://", ignoreCase = true) -> {
                val url = Uri.parse(value).getQueryParameter("url")
                if (url.isNullOrBlank()) toastError(R.string.vpn360_invalid_code) else importConfig(url)
            }

            value.contains("://") -> importConfig(value)
            else -> activateCode(value)
        }
    }

    /** Imports a link (vless:// or an https subscription) and selects its first server. */
    fun importConfig(text: String) {
        if (_uiState.value.importing) return
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val (count, countSub) = AngConfigManager.importBatchConfig(text, "", true)
                count + countSub > 0 && MmkvManager.decodeAllServerList().isNotEmpty()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "VPN 360 import failed", e)
                false
            }
            if (ok) {
                // Use the newest imported server
                MmkvManager.decodeAllServerList().firstOrNull()?.let { MmkvManager.setSelectServer(it) }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(importing = false) }
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
        if (BuildConfig.CENTRAL_URL.isEmpty()) {
            _uiState.update { it.copy(codeDialogOpen = true, codeError = R.string.vpn360_error_soon) }
            return
        }
        _uiState.update { it.copy(codeDialogOpen = true, codeBusy = true, codeError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = login(code)
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

    private data class LoginResult(val subUrl: String? = null, val error: Int? = null)

    private fun login(code: String): LoginResult {
        return try {
            val conn = URL(BuildConfig.CENTRAL_URL.trimEnd('/') + "/api/app/login").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("code", code).toString().toByteArray()) }
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            val json = runCatching { JSONObject(body) }.getOrNull()
            when {
                status == 401 || status == 404 -> LoginResult(error = R.string.vpn360_error_wrong_code)
                status !in 200..299 || json == null -> LoginResult(error = R.string.vpn360_error_offline)
                !json.optBoolean("active") -> LoginResult(error = R.string.vpn360_error_inactive)
                json.optString("subscription_url").startsWith("https://") ->
                    LoginResult(subUrl = json.optString("subscription_url"))

                else -> LoginResult(error = R.string.vpn360_error_offline)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "VPN 360 login failed", e)
            LoginResult(error = R.string.vpn360_error_offline)
        }
    }
}
