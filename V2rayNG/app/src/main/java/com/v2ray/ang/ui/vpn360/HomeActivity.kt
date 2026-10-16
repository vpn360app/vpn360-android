package com.v2ray.ang.ui.vpn360

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.delay

/**
 * VPN 360 home: one big button to connect, "scan code" to activate the account, and a small
 * menu (information and updates, language, support). There is no advanced mode for students.
 */
class HomeActivity : HelperBaseComponentActivity() {

    companion object {
        /** A link (subscription or vless://) to import as soon as the home opens. */
        const val EXTRA_IMPORT = "vpn360_import"
    }

    private val viewModel: HomeViewModel by viewModels()

    private val welcomeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            when (result.data?.getStringExtra(WelcomeActivity.EXTRA_ACTION)) {
                WelcomeActivity.ACTION_SCAN_QR -> scanCode()
                WelcomeActivity.ACTION_BUY -> openBuy(result.data?.getStringExtra(WelcomeActivity.EXTRA_PLAN))
            }
        }

    private val buyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refresh() }

    private val myPlanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            when (result.data?.getStringExtra(MyPlanActivity.EXTRA_ACTION)) {
                MyPlanActivity.ACTION_HOW -> openWelcome()
                MyPlanActivity.ACTION_BUY -> openBuy(result.data?.getStringExtra(MyPlanActivity.EXTRA_PLAN))
            }
        }

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startVpn()
            } else {
                viewModel.onConnectCancelled()
                viewModel.toast(R.string.vpn360_vpn_permission)
            }
        }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.onServiceMessage(intent?.getIntExtra("key", 0) ?: 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        SubscriptionUpdater.sync(this)
        if (savedInstanceState == null && !MmkvManager.decodeSettingsBool(WelcomeActivity.PREF_ONBOARDED, false) &&
            MmkvManager.decodeAllServerList().isEmpty()
        ) {
            if (!intent.hasExtra(EXTRA_IMPORT)) welcomeLauncher.launch(Intent(this, WelcomeActivity::class.java))
        }
        if (savedInstanceState == null) handleImportIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: Intent?) {
        val link = intent?.getStringExtra(EXTRA_IMPORT) ?: return
        intent.removeExtra(EXTRA_IMPORT)
        viewModel.importConfig(link)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, stateReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), Utils.receiverFlags()
        )
        MessageHelper.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
        viewModel.refresh()
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(stateReceiver)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to unregister receiver", e)
        }
    }

    @Composable
    override fun ScreenContent() {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        BackHandler { moveTaskToBack(false) }

        // Language change: cover the screen, apply the language (AppCompat rebuilds the
        // activity behind the curtain), then reveal the screen in the new language.
        val switch = uiState.langSwitch
        LaunchedEffect(switch) {
            if (switch == null) return@LaunchedEffect
            if (!switch.applied) {
                delay(420)
                viewModel.languageApplied()
                AppLocaleManager.setApplicationLanguage(switch.code)
            } else {
                delay(500)
                viewModel.finishLanguageSwitch()
            }
        }
        var lastSwitch by remember { mutableStateOf<LangSwitch?>(null) }
        SideEffect { if (switch != null) lastSwitch = switch }
        val shown = switch ?: lastSwitch

        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                uiState = uiState,
                canRenew = BuildConfig.SHOW_PLANS && Vpn360Api.enabled,
                hasSupport = BuildConfig.SALES_WHATSAPP.isNotEmpty(),
                onPower = ::onPower,
                onScan = ::scanCode,
                onOpenCode = viewModel::openCodeDialog,
                onCloseCode = viewModel::closeCodeDialog,
                onSubmitCode = viewModel::activateCode,
                onInfo = { startActivity(Intent(this@HomeActivity, AboutActivity::class.java)) },
                onMyPlan = { myPlanLauncher.launch(Intent(this@HomeActivity, MyPlanActivity::class.java)) },
                onHow = ::openWelcome,
                onSupport = ::openSupport,
                onRenew = ::renew,
                onLanguage = { option -> viewModel.startLanguageSwitch(option.code, option.native, option.greeting) }
            )
            LanguageOverlay(
                visible = switch != null,
                label = shown?.label.orEmpty(),
                greeting = shown?.greeting.orEmpty()
            )
        }
    }

    private fun scanCode() {
        launchQRCodeScanner { result ->
            if (!result.isNullOrBlank()) viewModel.onScanned(result)
        }
    }

    /** The welcome pages: what VPN 360 is for, then the plans. */
    private fun openWelcome() {
        welcomeLauncher.launch(Intent(this, WelcomeActivity::class.java))
    }

    private fun openBuy(planId: String?) {
        if (!Vpn360Api.enabled) return
        buyLauncher.launch(Intent(this, BuyActivity::class.java).putExtra(BuyActivity.EXTRA_PLAN, planId))
    }

    /** Expired plan: buy again in the app (direct download) or on the website (Google Play). */
    private fun renew() {
        when {
            BuildConfig.SHOW_PLANS && Vpn360Api.enabled -> openBuy(null)
            Vpn360Api.enabled -> openUrl(BuildConfig.CENTRAL_URL.trimEnd('/') + "/cuenta")
            else -> openSupport()
        }
    }

    private fun onPower() {
        when (viewModel.uiState.value.vpnState) {
            VpnState.Connected -> LauncherManager.stopService(this)
            VpnState.Connecting -> {
                LauncherManager.stopService(this)
                viewModel.onConnectCancelled()
            }

            VpnState.Disconnected -> {
                if (!viewModel.uiState.value.hasAccount) {
                    scanCode()
                    return
                }
                viewModel.onConnectRequested()
                val intent = if (SettingsManager.isVpnMode()) VpnService.prepare(this) else null
                if (intent == null) startVpn() else vpnPermission.launch(intent)
            }
        }
    }

    private fun startVpn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    private fun openSupport() {
        if (BuildConfig.SALES_WHATSAPP.isEmpty()) return
        openUrl(
            "https://wa.me/" + BuildConfig.SALES_WHATSAPP + "?text=" +
                Uri.encode(getString(R.string.vpn360_whatsapp_help_msg))
        )
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Cannot open $url", e)
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    canRenew: Boolean,
    hasSupport: Boolean,
    onPower: () -> Unit,
    onScan: () -> Unit,
    onOpenCode: () -> Unit,
    onCloseCode: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onInfo: () -> Unit,
    onMyPlan: () -> Unit,
    onHow: () -> Unit,
    onSupport: () -> Unit,
    onRenew: () -> Unit,
    onLanguage: (LangOption) -> Unit
) {
    var languageOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        HomeTopBar(
            language = uiState.language,
            hasSupport = hasSupport,
            onInfo = onInfo,
            onMyPlan = onMyPlan,
            onHow = onHow,
            onSupport = onSupport,
            onLanguage = { languageOpen = true }
        )
        AnimatedVisibility(
            visible = uiState.updateVersion != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            UpdateBanner(version = uiState.updateVersion.orEmpty(), onClick = onInfo)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusChip(uiState.vpnState, uiState.hasAccount)
            Spacer(Modifier.height(20.dp))
            PowerButton(
                state = uiState.vpnState,
                enabled = !uiState.importing,
                onClick = onPower
            )
            Spacer(Modifier.height(24.dp))
            StatusTexts(uiState)
        }

        AnimatedVisibility(
            visible = uiState.expired && uiState.hasAccount,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ExpiredCard(canRenew = canRenew || hasSupport, onRenew = onRenew)
        }
        BottomActions(uiState, onScan, onOpenCode)
    }

    if (uiState.codeDialogOpen) {
        CodeDialog(
            busy = uiState.codeBusy,
            error = uiState.codeError,
            onDismiss = onCloseCode,
            onSubmit = onSubmitCode
        )
    }
    if (languageOpen) {
        LanguageSheet(
            current = uiState.language,
            onDismiss = { languageOpen = false },
            onSelect = { option ->
                languageOpen = false
                onLanguage(option)
            }
        )
    }
}

@Composable
private fun HomeTopBar(
    language: String,
    hasSupport: Boolean,
    onInfo: () -> Unit,
    onMyPlan: () -> Unit,
    onHow: () -> Unit,
    onSupport: () -> Unit,
    onLanguage: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val badge = langOptions.firstOrNull { it.code == language }?.badge

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(3.dp, primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("360", color = primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.app_name),
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Surface(
            onClick = onLanguage,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_translate_24dp),
                    contentDescription = stringResource(R.string.vpn360_language),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                if (badge != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painterResource(R.drawable.ic_more_vert_24dp),
                    contentDescription = stringResource(R.string.vpn360_menu),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vpn360_menu_my_plan)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_subscriptions_24dp), contentDescription = null) },
                    onClick = { menuOpen = false; onMyPlan() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vpn360_menu_how)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_promotion_24dp), contentDescription = null) },
                    onClick = { menuOpen = false; onHow() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vpn360_menu_info)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_check_update_24dp), contentDescription = null) },
                    onClick = { menuOpen = false; onInfo() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vpn360_language)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_translate_24dp), contentDescription = null) },
                    onClick = { menuOpen = false; onLanguage() }
                )
                if (hasSupport) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vpn360_menu_support)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_feedback_24dp), contentDescription = null) },
                        onClick = { menuOpen = false; onSupport() }
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_check_update_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.vpn360_update_banner, version),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ExpiredCard(canRenew: Boolean, onRenew: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.vpn360_expired_title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.vpn360_expired_text),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (canRenew) {
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onRenew,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text(stringResource(R.string.vpn360_renew), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun StatusChip(state: VpnState, hasAccount: Boolean) {
    val accent = MaterialTheme.colorScheme.tertiary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val color by animateColorAsState(
        when (state) {
            VpnState.Connected -> accent
            VpnState.Connecting -> MaterialTheme.colorScheme.secondary
            VpnState.Disconnected -> muted
        },
        tween(400),
        label = "chipColor"
    )
    val text = when {
        state == VpnState.Connected -> R.string.vpn360_status_connected
        state == VpnState.Connecting -> R.string.vpn360_status_connecting
        !hasAccount -> R.string.vpn360_no_account_title
        else -> R.string.vpn360_status_disconnected
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "chipText"
            ) { res ->
                Text(stringResource(res), color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun PowerButton(state: VpnState, enabled: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.tertiary
    val buttonColor by animateColorAsState(
        if (state == VpnState.Connected) accent else primary,
        tween(500),
        label = "powerColor"
    )
    val iconColor by animateColorAsState(
        if (state == VpnState.Connected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
        tween(500),
        label = "powerIconColor"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )

    val transition = rememberInfiniteTransition(label = "power")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "breathe"
    )

    val actionLabel = stringResource(
        if (state == VpnState.Disconnected) R.string.vpn360_connect_desc else R.string.vpn360_disconnect_desc
    )
    val stateLabel = stringResource(
        when (state) {
            VpnState.Connected -> R.string.vpn360_title_connected
            VpnState.Connecting -> R.string.vpn360_title_connecting
            VpnState.Disconnected -> R.string.vpn360_title_disconnected
        }
    )

    Box(modifier = Modifier.size(270.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2f
            val baseR = maxR * 0.62f
            when (state) {
                VpnState.Connected -> {
                    // Two expanding rings: "protected" pulse
                    for (i in 0..1) {
                        val p = (pulse + i * 0.5f) % 1f
                        drawCircle(
                            color = buttonColor.copy(alpha = 0.28f * (1f - p)),
                            radius = baseR + (maxR - baseR) * p
                        )
                    }
                }

                VpnState.Connecting -> {
                    drawCircle(color = buttonColor.copy(alpha = 0.10f), radius = maxR * 0.9f)
                    val arcR = maxR * 0.82f
                    drawArc(
                        color = buttonColor,
                        startAngle = rotation,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(center.x - arcR, center.y - arcR),
                        size = Size(arcR * 2, arcR * 2),
                        style = Stroke(width = maxR * 0.05f, cap = StrokeCap.Round)
                    )
                }

                VpnState.Disconnected -> {
                    drawCircle(color = buttonColor.copy(alpha = breathe), radius = maxR * 0.95f)
                    drawCircle(color = buttonColor.copy(alpha = breathe + 0.06f), radius = maxR * 0.78f)
                }
            }
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = buttonColor,
            shadowElevation = 10.dp,
            interactionSource = interaction,
            modifier = Modifier
                .size(168.dp)
                .scale(pressScale)
                .semantics {
                    contentDescription = actionLabel
                    stateDescription = stateLabel
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!enabled) {
                    CircularProgressIndicator(color = iconColor, strokeWidth = 3.dp, modifier = Modifier.size(56.dp))
                } else {
                    Canvas(modifier = Modifier.size(64.dp)) {
                        val w = size.minDimension
                        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
                        val r = w * 0.38f
                        drawArc(
                            iconColor, startAngle = -55f, sweepAngle = 290f, useCenter = false,
                            topLeft = Offset(center.x - r, center.y - r + w * 0.06f), size = Size(r * 2, r * 2), style = stroke
                        )
                        drawLine(iconColor, Offset(center.x, w * 0.06f), Offset(center.x, w * 0.46f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTexts(uiState: HomeUiState) {
    val title = when (uiState.vpnState) {
        VpnState.Connected -> R.string.vpn360_title_connected
        VpnState.Connecting -> R.string.vpn360_title_connecting
        VpnState.Disconnected -> R.string.vpn360_title_disconnected
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.vpnState, uiState.connectedSince) {
        while (uiState.vpnState == VpnState.Connected) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 3 })
            },
            label = "title"
        ) { res ->
            Text(
                stringResource(res),
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(6.dp))
        val sub = when {
            uiState.importing -> stringResource(R.string.vpn360_importing)
            !uiState.hasAccount -> stringResource(R.string.vpn360_no_account_text)
            uiState.vpnState == VpnState.Connected -> {
                val elapsed = ((now - uiState.connectedSince).coerceAtLeast(0L)) / 1000
                val time = "%02d:%02d:%02d".format(elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)
                if (uiState.serverName.isNotEmpty()) "${uiState.serverName} · $time" else time
            }

            uiState.vpnState == VpnState.Connecting -> uiState.serverName
            else -> stringResource(R.string.vpn360_tap_to_connect)
        }
        Text(
            sub,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BottomActions(uiState: HomeUiState, onScan: () -> Unit, onOpenCode: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimatedVisibility(visible = !uiState.hasAccount) {
            Button(
                onClick = onScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Icon(painterResource(R.drawable.ic_scan_24dp), contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.vpn360_scan_code), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (uiState.hasAccount) {
            TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_scan_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vpn360_scan_other), color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            TextButton(onClick = onOpenCode, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.vpn360_have_code),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CodeDialog(busy: Boolean, error: Int?, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.vpn360_code_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Text(stringResource(R.string.vpn360_code_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == ' ' }.take(24) },
                    label = { Text(stringResource(R.string.vpn360_code_hint)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (!busy && code.isNotBlank()) onSubmit(code) }),
                    supportingText = error?.let { { Text(stringResource(it)) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(code) },
                enabled = !busy && code.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vpn360_activating))
                } else {
                    Text(stringResource(R.string.vpn360_activate))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.vpn360_cancel)) }
        }
    )
}
