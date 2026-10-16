package com.v2ray.ang.ui.vpn360

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BuyStep { Plans, Form, Waiting, Done }

data class BuyUiState(
    val step: BuyStep = BuyStep.Plans,
    val planId: String? = null,
    val name: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val accepted: Boolean = false,
    val busy: Boolean = false,
    val errorRes: Int? = null,
    val errorText: String? = null,
    val checkoutUrl: String? = null,
    val accessCode: String? = null,
    val plans: List<PlanUi> = emptyList(),
    val rate: Double = 3.45
)

/**
 * Buying a plan inside the app: the account server creates the Mercado Pago subscription and
 * returns its checkout page; the app waits for the payment and then activates itself.
 */
class BuyViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(BuyUiState())
    val uiState: StateFlow<BuyUiState> = _uiState.asStateFlow()
    private var polling: Job? = null

    fun start(planId: String?) {
        if (_uiState.value.planId != null || _uiState.value.step != BuyStep.Plans) return
        val pending = MmkvManager.decodeSettingsString(Vpn360Api.PREF_PENDING_REF).orEmpty()
        _uiState.update {
            it.copy(
                name = MmkvManager.decodeSettingsString(Vpn360Api.PREF_BUYER_NAME).orEmpty(),
                email = MmkvManager.decodeSettingsString(Vpn360Api.PREF_BUYER_EMAIL).orEmpty(),
                whatsapp = MmkvManager.decodeSettingsString(Vpn360Api.PREF_BUYER_WHATS).orEmpty(),
                plans = PriceBook.plans,
                rate = PriceBook.rate,
                planId = planById(planId)?.id,
                step = if (planById(planId) != null) BuyStep.Form else BuyStep.Plans
            )
        }
        if (pending.isNotEmpty()) {
            _uiState.update { it.copy(step = BuyStep.Waiting) }
            startPolling(pending)
        }
    }

    fun choosePlan(id: String) = _uiState.update { it.copy(planId = id, step = BuyStep.Form, errorRes = null, errorText = null) }

    fun backToPlans() = _uiState.update { it.copy(step = BuyStep.Plans, errorRes = null, errorText = null) }

    fun edit(name: String? = null, email: String? = null, whatsapp: String? = null, accepted: Boolean? = null) =
        _uiState.update {
            it.copy(
                name = name ?: it.name,
                email = email ?: it.email,
                whatsapp = whatsapp ?: it.whatsapp,
                accepted = accepted ?: it.accepted,
                errorRes = null,
                errorText = null
            )
        }

    /** Validates, asks the server for the checkout page and returns it to the activity to open. */
    fun pay(lang: String, onCheckout: (String) -> Unit) {
        val s = _uiState.value
        val plan = planById(s.planId, s.plans) ?: return
        val name = s.name.trim()
        val email = s.email.trim().lowercase()
        val whats = s.whatsapp.trim()
        val error = when {
            name.length < 3 -> R.string.vpn360_err_name
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> R.string.vpn360_err_email
            whats.count { it.isDigit() } < 7 -> R.string.vpn360_err_whats
            !s.accepted -> R.string.vpn360_err_accept
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(errorRes = error, errorText = null) }
            return
        }
        _uiState.update { it.copy(busy = true, errorRes = null, errorText = null) }
        viewModelScope.launch(Dispatchers.IO) {
            MmkvManager.encodeSettings(Vpn360Api.PREF_BUYER_NAME, name)
            MmkvManager.encodeSettings(Vpn360Api.PREF_BUYER_EMAIL, email)
            MmkvManager.encodeSettings(Vpn360Api.PREF_BUYER_WHATS, whats)
            val r = Vpn360Api.subscribe(name, email, whats, plan.id, lang)
            withContext(Dispatchers.Main) {
                if (r.ref != null && r.checkoutUrl != null) {
                    MmkvManager.encodeSettings(Vpn360Api.PREF_PENDING_REF, r.ref)
                    MmkvManager.encodeSettings(Vpn360Api.PREF_PENDING_AT, System.currentTimeMillis())
                    _uiState.update { it.copy(busy = false, step = BuyStep.Waiting, checkoutUrl = r.checkoutUrl) }
                    onCheckout(r.checkoutUrl)
                    startPolling(r.ref)
                } else {
                    _uiState.update { it.copy(busy = false, errorRes = r.errorRes, errorText = r.error) }
                }
            }
        }
    }

    private fun startPolling(ref: String) {
        polling?.cancel()
        polling = viewModelScope.launch(Dispatchers.IO) {
            val until = System.currentTimeMillis() + 45 * 60_000L
            while (isActive && System.currentTimeMillis() < until) {
                val order = Vpn360Api.order(ref)
                if (!order.known) {
                    clearPending()
                    _uiState.update { it.copy(step = BuyStep.Form) }
                    return@launch
                }
                if (order.active && order.subUrl != null) {
                    val ok = try {
                        val (c, cs) = AngConfigManager.importBatchConfig(order.subUrl, "", true)
                        (c + cs > 0 || MmkvManager.decodeSubscriptions().any { it.subscription.url == order.subUrl }) &&
                            MmkvManager.decodeAllServerList().isNotEmpty()
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "VPN 360 activation after payment failed", e)
                        false
                    }
                    if (ok) {
                        MmkvManager.decodeAllServerList().firstOrNull()?.let { MmkvManager.setSelectServer(it) }
                        MmkvManager.encodeSettings(WelcomeActivity.PREF_ONBOARDED, true)
                        clearPending()
                        _uiState.update { it.copy(step = BuyStep.Done, accessCode = order.accessCode) }
                        return@launch
                    }
                }
                delay(4_000)
            }
        }
    }

    /** Polls right away (e.g. when the user comes back from Mercado Pago). */
    fun checkNow() {
        val ref = MmkvManager.decodeSettingsString(Vpn360Api.PREF_PENDING_REF).orEmpty()
        if (ref.isNotEmpty() && _uiState.value.step == BuyStep.Waiting) startPolling(ref)
    }

    fun cancelWaiting() {
        polling?.cancel()
        clearPending()
        _uiState.update { it.copy(step = BuyStep.Form) }
    }

    private fun clearPending() {
        MmkvManager.encodeSettings(Vpn360Api.PREF_PENDING_REF, "")
        MmkvManager.encodeSettings(Vpn360Api.PREF_PENDING_AT, 0L)
    }
}

class BuyActivity : BaseComponentActivity() {

    companion object {
        const val EXTRA_PLAN = "vpn360_plan"
    }

    private val viewModel: BuyViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start(intent.getStringExtra(EXTRA_PLAN))
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkNow()
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        BackHandler {
            when (state.step) {
                BuyStep.Form -> viewModel.backToPlans()
                BuyStep.Done -> finishOk()
                else -> finish()
            }
        }
        BuyScreen(
            state = state,
            onBack = {
                when (state.step) {
                    BuyStep.Form -> viewModel.backToPlans()
                    BuyStep.Done -> finishOk()
                    else -> finish()
                }
            },
            onChoosePlan = { viewModel.choosePlan(it.id) },
            onEdit = viewModel::edit,
            onTerms = { openUrl(BuildConfig.CENTRAL_URL.trimEnd('/') + "/terminos") },
            onPay = { viewModel.pay(resources.configuration.locales[0].language) { url -> openUrl(url) } },
            onOpenCheckout = { state.checkoutUrl?.let { openUrl(it) } },
            onCancelWaiting = viewModel::cancelWaiting,
            onDone = ::finishOk
        )
    }

    private fun finishOk() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Cannot open $url", e)
        }
    }
}

@Composable
private fun BuyScreen(
    state: BuyUiState,
    onBack: () -> Unit,
    onChoosePlan: (PlanUi) -> Unit,
    onEdit: (String?, String?, String?, Boolean?) -> Unit,
    onTerms: () -> Unit,
    onPay: () -> Unit,
    onOpenCheckout: () -> Unit,
    onCancelWaiting: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
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
            if (state.step == BuyStep.Form) {
                Text(stringResource(R.string.vpn360_buy_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(200)))
            },
            label = "buyStep",
            modifier = Modifier.weight(1f)
        ) { step ->
            when (step) {
                BuyStep.Plans -> PlansList(
                    canBuy = true, onChoosePlan = onChoosePlan, modifier = Modifier.fillMaxSize(),
                    plans = state.plans, rate = state.rate
                )
                BuyStep.Form -> BuyForm(state, onEdit, onTerms, onPay)
                BuyStep.Waiting -> WaitingPane(onOpenCheckout, onCancelWaiting, canReopen = state.checkoutUrl != null)
                BuyStep.Done -> DonePane(state.accessCode, onDone)
            }
        }
    }
}

@Composable
private fun BuyForm(
    state: BuyUiState,
    onEdit: (String?, String?, String?, Boolean?) -> Unit,
    onTerms: () -> Unit,
    onPay: () -> Unit
) {
    val plan = planById(state.planId, state.plans) ?: return
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(plan.name), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(billingLine(plan, state.rate), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.vpn360_pen_note, pen(plan.perMonth)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(usd(plan.perMonth, state.rate), color = primary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(stringResource(R.string.vpn360_per_month), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        if (plan.id == "estudiante") {
            Text(stringResource(R.string.vpn360_student_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = { onEdit(it.take(80), null, null, null) },
            label = { Text(stringResource(R.string.vpn360_f_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = { onEdit(null, it.trim().take(120), null, null) },
            label = { Text(stringResource(R.string.vpn360_f_email)) },
            supportingText = { Text(stringResource(R.string.vpn360_f_email_help)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.whatsapp,
            onValueChange = { v -> onEdit(null, null, v.filter { it.isDigit() || it in "+ " }.take(20), null) },
            label = { Text(stringResource(R.string.vpn360_f_whats)) },
            placeholder = { Text("+51 9…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox) { onEdit(null, null, null, !state.accepted) }
        ) {
            Checkbox(checked = state.accepted, onCheckedChange = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.vpn360_accept_terms), fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = onTerms) { Text(stringResource(R.string.vpn360_read_terms), color = MaterialTheme.colorScheme.secondary) }
        val error = state.errorText ?: state.errorRes?.let { stringResource(it) }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
        Button(
            onClick = onPay,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            if (state.busy) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.vpn360_pay_mp), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
        Text(
            stringResource(R.string.vpn360_pay_note),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WaitingPane(onOpenCheckout: () -> Unit, onCancel: () -> Unit, canReopen: Boolean) {
    val pulse = rememberInfiniteTransition(label = "wait")
    val scale by pulse.animateFloat(0.9f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(90.dp).background(primary, CircleShape), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.vpn360_waiting_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.vpn360_waiting_text),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (canReopen) {
            Button(
                onClick = onOpenCheckout,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text(stringResource(R.string.vpn360_open_mp_again)) }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.vpn360_cancel), color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun DonePane(accessCode: String?, onDone: () -> Unit) {
    val primary = MaterialTheme.colorScheme.tertiary
    val onColor = MaterialTheme.colorScheme.onTertiary
    val draw = rememberInfiniteTransition(label = "done")
    val glow by draw.animateFloat(0.10f, 0.22f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "g")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(150.dp).background(primary.copy(alpha = glow), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(104.dp).background(primary, CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(52.dp)) {
                    val p = Path().apply {
                        moveTo(size.width * 0.16f, size.height * 0.54f)
                        lineTo(size.width * 0.42f, size.height * 0.78f)
                        lineTo(size.width * 0.86f, size.height * 0.26f)
                    }
                    drawPath(p, onColor, style = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.vpn360_paid_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.vpn360_paid_text),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!accessCode.isNullOrEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.vpn360_access_code_label), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    accessCode,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                )
            }
            Text(
                stringResource(R.string.vpn360_code_sent),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text(stringResource(R.string.vpn360_start), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    }
}
