package com.v2ray.ang.ui.vpn360

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

enum class AccountKind { Loading, Server, Test, None, Unavailable }

data class MyPlanUiState(
    val kind: AccountKind = AccountKind.Loading,
    val account: Vpn360Api.AccountInfo? = null,
    val shareLink: String? = null,
    val qr: Bitmap? = null,
    val plans: List<PlanUi> = emptyList(),
    val rate: Double = 3.45
)

/** "Mi plan": the account's plan and next charge, its access code, a QR for other devices, and the plans. */
class MyPlanViewModel(application: Application) : BaseViewModel(application) {
    private val _uiState = MutableStateFlow(MyPlanUiState())
    val uiState: StateFlow<MyPlanUiState> = _uiState.asStateFlow()

    fun load(lang: String) {
        _uiState.update { it.copy(plans = PriceBook.plans, rate = PriceBook.rate) }
        viewModelScope.launch(Dispatchers.IO) {
            if (Vpn360Api.refreshPlans(lang)) {
                _uiState.update { it.copy(plans = PriceBook.plans, rate = PriceBook.rate) }
            }
            val sub = Vpn360Api.subscriptionUrl()
            val servers = MmkvManager.decodeAllServerList()
            val share = sub ?: directLink()
            val kind: AccountKind
            var account: Vpn360Api.AccountInfo? = null
            when {
                sub != null -> {
                    account = Vpn360Api.account(lang)
                    kind = if (account != null) AccountKind.Server else AccountKind.Unavailable
                }

                servers.isNotEmpty() -> kind = AccountKind.Test
                else -> kind = AccountKind.None
            }
            val qr = share?.let { QRCodeDecoder.createQRCode(it, 640) }
            _uiState.update { it.copy(kind = kind, account = account, shareLink = share, qr = qr) }
        }
    }

    /** Test or courtesy accounts are a single vless link of our own server. */
    private fun directLink(): String? {
        val guid = MmkvManager.getSelectServer() ?: MmkvManager.decodeAllServerList().firstOrNull() ?: return null
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        if (profile.configType != EConfigType.VLESS) return null
        return runCatching { EConfigType.VLESS.protocolScheme + VlessFmt.toUri(profile) }.getOrNull()
    }
}

class MyPlanActivity : BaseComponentActivity() {

    companion object {
        const val EXTRA_ACTION = "vpn360_myplan_action"
        const val ACTION_HOW = "how"
        const val ACTION_BUY = "buy"
        const val EXTRA_PLAN = "vpn360_plan"
    }

    private val viewModel: MyPlanViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.load(resources.configuration.locales[0].language)
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val active = state.account?.active == true || state.kind == AccountKind.Test
        MyPlanScreen(
            state = state,
            canBuy = BuildConfig.SHOW_PLANS && Vpn360Api.enabled && !active,
            onBack = { finish() },
            onCopyLink = {
                state.shareLink?.let {
                    Utils.setClipboard(this, it)
                    toast(R.string.vpn360_copied)
                }
            },
            onCopyCode = { code ->
                Utils.setClipboard(this, code)
                toast(R.string.vpn360_copied_code)
            },
            onHow = { finishWith(ACTION_HOW, null) },
            onChoosePlan = { plan -> finishWith(ACTION_BUY, plan.id) }
        )
    }

    private fun finishWith(action: String, plan: String?) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ACTION, action).putExtra(EXTRA_PLAN, plan))
        finish()
    }
}

@Composable
private fun MyPlanScreen(
    state: MyPlanUiState,
    canBuy: Boolean,
    onBack: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyCode: (String) -> Unit,
    onHow: () -> Unit,
    onChoosePlan: (PlanUi) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back_24dp),
                    contentDescription = stringResource(R.string.vpn360_back),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(stringResource(R.string.vpn360_menu_my_plan), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedContent(
                targetState = state.kind,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "account",
                modifier = Modifier.padding(horizontal = 20.dp)
            ) { kind ->
                AccountCard(kind, state.account, state.rate)
            }
            val code = state.account?.accessCode.orEmpty()
            if (code.isNotEmpty()) {
                SectionCard(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.vpn360_access_code_label), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            code,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onCopyCode(code) }) {
                            Text(stringResource(R.string.vpn360_copy), color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text(stringResource(R.string.vpn360_code_use), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.qr != null) {
                SectionCard(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.vpn360_other_devices), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(stringResource(R.string.vpn360_other_devices_text), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    val image = remember(state.qr) { state.qr.asImageBitmap() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Image(
                                bitmap = image,
                                contentDescription = stringResource(R.string.vpn360_qr_desc),
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(210.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCopyLink, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.vpn360_copy_link))
                    }
                    Text(
                        stringResource(R.string.vpn360_link_private),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Text(
                stringResource(R.string.vpn360_plans_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp)
            )
            PlansList(
                canBuy = canBuy,
                onChoosePlan = onChoosePlan,
                plans = state.plans,
                rate = state.rate,
                currentPlanId = state.account?.takeIf { it.active }?.planId,
                showTitle = false,
                scrollable = false
            )
            OutlinedButton(
                onClick = onHow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.vpn360_how_it_works)) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AccountCard(kind: AccountKind, account: Vpn360Api.AccountInfo?, rate: Double) {
    val locale = LocalConfiguration.current.locales[0]
    val fmt = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    SectionCard(Modifier, highlight = kind == AccountKind.Server && account?.active == true) {
        when (kind) {
            AccountKind.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.vpn360_checking))
            }

            AccountKind.Server -> {
                val a = account ?: return@SectionCard
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.planName, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    StatusPill(a.status)
                }
                if (a.amountPen > 0) {
                    Text(
                        "${usd(a.amountPen, rate)} · ${pen(a.amountPen)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (a.billing.isNotEmpty()) {
                    Text(a.billing, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                if (a.nextCharge > 0) {
                    InfoLine(stringResource(R.string.vpn360_next_charge), fmt.format(Date(a.nextCharge * 1000)))
                } else {
                    InfoLine(stringResource(R.string.vpn360_next_charge), stringResource(R.string.vpn360_no_autorenew))
                }
                if (a.expiresAt > 0) {
                    InfoLine(
                        stringResource(if (a.active) R.string.vpn360_access_until else R.string.vpn360_expired_on),
                        fmt.format(Date(a.expiresAt * 1000))
                    )
                }
            }

            AccountKind.Test -> {
                Text(stringResource(R.string.vpn360_test_account), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.vpn360_test_account_text), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AccountKind.None -> {
                Text(stringResource(R.string.vpn360_no_account_title), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.vpn360_no_plan_text), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AccountKind.Unavailable -> {
                Text(stringResource(R.string.vpn360_menu_my_plan), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.vpn360_myplan_offline), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun StatusPill(status: String) {
    val (label, good) = when (status) {
        "activo" -> R.string.vpn360_st_active to true
        "cancelado" -> R.string.vpn360_st_cancelled to true
        "vencido" -> R.string.vpn360_st_expired to false
        "pendiente_pago" -> R.string.vpn360_st_pending to false
        else -> R.string.vpn360_st_disabled to false
    }
    val color = if (good) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(
            stringResource(label),
            color = if (good) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SectionCard(modifier: Modifier, highlight: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (highlight) 2.dp else 1.dp,
            if (highlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
