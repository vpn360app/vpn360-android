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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.delay

/**
 * VPN 360 home: one big button to connect, "scan code" to activate the account, and a small
 * menu for plans, language, support and the advanced (full v2rayNG) mode.
 */
class HomeActivity : HelperBaseComponentActivity() {

    companion object {
        /** A link (subscription or vless://) to import as soon as the home opens. */
        const val EXTRA_IMPORT = "vpn360_import"
    }

    private val viewModel: HomeViewModel by viewModels()

    private val welcomeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK &&
                result.data?.getStringExtra(WelcomeActivity.EXTRA_ACTION) == WelcomeActivity.ACTION_SCAN_QR
            ) {
                scanCode()
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
        MmkvManager.encodeSettings(WelcomeActivity.PREF_ONBOARDED, true)
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
        HomeScreen(
            uiState = uiState,
            showPlans = BuildConfig.SHOW_PLANS,
            hasSupport = BuildConfig.SALES_WHATSAPP.isNotEmpty(),
            onPower = ::onPower,
            onScan = ::scanCode,
            onOpenCode = viewModel::openCodeDialog,
            onCloseCode = viewModel::closeCodeDialog,
            onSubmitCode = viewModel::activateCode,
            onPlans = { welcomeLauncher.launch(Intent(this, WelcomeActivity::class.java)) },
            onAdvanced = { startActivity(Intent(this, MainActivity::class.java).putExtra("vpn360_skip_welcome", true)) },
            onSupport = ::openSupport,
            onLanguage = { code -> AppLocaleManager.setApplicationLanguage(code) }
        )
    }

    private fun scanCode() {
        launchQRCodeScanner { result ->
            if (!result.isNullOrBlank()) viewModel.onScanned(result)
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
        try {
            val url = "https://wa.me/" + BuildConfig.SALES_WHATSAPP + "?text=" +
                Uri.encode(getString(R.string.vpn360_whatsapp_help_msg))
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Cannot open WhatsApp", e)
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    showPlans: Boolean,
    hasSupport: Boolean,
    onPower: () -> Unit,
    onScan: () -> Unit,
    onOpenCode: () -> Unit,
    onCloseCode: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onPlans: () -> Unit,
    onAdvanced: () -> Unit,
    onSupport: () -> Unit,
    onLanguage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        HomeTopBar(
            showPlans = showPlans,
            hasSupport = hasSupport,
            onPlans = onPlans,
            onAdvanced = onAdvanced,
            onSupport = onSupport,
            onLanguage = onLanguage
        )

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
}

@Composable
private fun HomeTopBar(
    showPlans: Boolean,
    hasSupport: Boolean,
    onPlans: () -> Unit,
    onAdvanced: () -> Unit,
    onSupport: () -> Unit,
    onLanguage: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary

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
        IconButton(onClick = { languageOpen = true }) {
            Icon(
                painterResource(R.drawable.ic_translate_24dp),
                contentDescription = stringResource(R.string.vpn360_language),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    text = { Text(stringResource(if (showPlans) R.string.vpn360_menu_plans else R.string.vpn360_plans_help)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_promotion_24dp), contentDescription = null) },
                    onClick = { menuOpen = false; onPlans() }
                )
                if (hasSupport) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vpn360_menu_support)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_feedback_24dp), contentDescription = null) },
                        onClick = { menuOpen = false; onSupport() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vpn360_language)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_translate_24dp), contentDescription = null) },
                    onClick = { menuOpen = false; languageOpen = true }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vpn360_menu_advanced)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = null) },
                    onClick = { menuOpen = false; onAdvanced() }
                )
            }
        }
    }

    if (languageOpen) {
        LanguageDialog(
            onDismiss = { languageOpen = false },
            onSelect = { code -> languageOpen = false; onLanguage(code) }
        )
    }
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val options = listOf(
        "es" to "Español",
        "en" to "English",
        "ru" to "Русский",
        "auto" to stringResource(R.string.vpn360_language_auto)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vpn360_language)) },
        text = {
            Column {
                options.forEach { (code, label) ->
                    Text(
                        label,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { onSelect(code) }
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.vpn360_cancel)) } }
    )
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
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(56.dp))
                } else {
                    Canvas(modifier = Modifier.size(64.dp)) {
                        val w = size.minDimension
                        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
                        val r = w * 0.38f
                        drawArc(
                            Color.White, startAngle = -55f, sweepAngle = 290f, useCenter = false,
                            topLeft = Offset(center.x - r, center.y - r + w * 0.06f), size = Size(r * 2, r * 2), style = stroke
                        )
                        drawLine(Color.White, Offset(center.x, w * 0.06f), Offset(center.x, w * 0.46f), strokeWidth = stroke.width, cap = StrokeCap.Round)
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
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White)
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
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
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
