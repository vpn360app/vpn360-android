package com.v2ray.ang.ui.vpn360

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Update check state for the information screen. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val url: String) : UpdateState
    data object Failed : UpdateState
}

class AboutViewModel(application: Application) : BaseViewModel(application) {
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    fun checkUpdate() {
        if (_update.value == UpdateState.Checking) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = try {
                val r = UpdateCheckerManager.checkForUpdate(false)
                if (r.hasUpdate && !r.latestVersion.isNullOrEmpty()) {
                    MmkvManager.encodeSettings("vpn360_update_version", r.latestVersion)
                    UpdateState.Available(r.latestVersion, r.downloadUrl ?: universalApk())
                } else {
                    MmkvManager.encodeSettings("vpn360_update_version", "")
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "VPN 360 update check failed", e)
                UpdateState.Failed
            }
        }
    }

    private fun universalApk() = AppConfig.APP_URL + "/releases/latest/download/VPN360-universal.apk"
}

/** Information: version and updates, website, support, privacy and open-source licence. */
class AboutActivity : BaseComponentActivity() {

    private val viewModel: AboutViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val update by viewModel.update.collectAsStateWithLifecycle()
        val web = Vpn360Api.enabled && BuildConfig.DISTRIBUTION == "Web"
        AboutScreen(
            update = update,
            canUpdate = BuildConfig.DISTRIBUTION == "Web",
            website = BuildConfig.CENTRAL_URL.trimEnd('/').takeIf { Vpn360Api.enabled },
            hasSupport = BuildConfig.SALES_WHATSAPP.isNotEmpty(),
            onBack = { finish() },
            onCheck = viewModel::checkUpdate,
            onDownload = { url -> openUrl(url) },
            onWebsite = { openUrl(BuildConfig.CENTRAL_URL.trimEnd('/') + "/cuenta") },
            onSupport = {
                openUrl(
                    "https://wa.me/" + BuildConfig.SALES_WHATSAPP + "?text=" +
                        Uri.encode(getString(R.string.vpn360_whatsapp_help_msg))
                )
            },
            onPrivacy = {
                openUrl(
                    if (Vpn360Api.enabled) BuildConfig.CENTRAL_URL.trimEnd('/') + "/privacidad"
                    else AppConfig.APP_URL + "/blob/main/PRIVACIDAD.md"
                )
            },
            onSource = { openUrl(AppConfig.APP_URL) },
            showWebsiteSoon = !web && BuildConfig.SHOW_PLANS
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

@Composable
private fun AboutScreen(
    update: UpdateState,
    canUpdate: Boolean,
    website: String?,
    hasSupport: Boolean,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: (String) -> Unit,
    onWebsite: () -> Unit,
    onSupport: () -> Unit,
    onPrivacy: () -> Unit,
    onSource: () -> Unit,
    showWebsiteSoon: Boolean
) {
    val primary = MaterialTheme.colorScheme.primary
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
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back_24dp),
                    contentDescription = stringResource(R.string.vpn360_back),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                stringResource(R.string.vpn360_info_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Brand + version
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .border(4.dp, MaterialTheme.colorScheme.onPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("360", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.app_name), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    stringResource(R.string.vpn360_version, BuildConfig.VERSION_NAME),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }

            if (canUpdate) {
                InfoCard {
                    Text(stringResource(R.string.vpn360_updates), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = update,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "update"
                    ) { state ->
                        Column {
                            when (state) {
                                UpdateState.Idle -> Unit
                                UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.vpn360_checking))
                                }

                                UpdateState.UpToDate -> Text(
                                    stringResource(R.string.vpn360_up_to_date),
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                is UpdateState.Available -> {
                                    Text(
                                        stringResource(R.string.vpn360_update_available, state.version),
                                        fontWeight = FontWeight.Bold,
                                        color = primary
                                    )
                                    Text(
                                        stringResource(R.string.vpn360_update_hint),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { onDownload(state.url) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                                    ) { Text(stringResource(R.string.vpn360_download), fontWeight = FontWeight.Bold) }
                                }

                                UpdateState.Failed -> Text(
                                    stringResource(R.string.vpn360_update_error),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    if (update !is UpdateState.Available) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onCheck,
                            enabled = update != UpdateState.Checking,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(R.string.vpn360_check_updates)) }
                    }
                }
            }

            if (website != null || showWebsiteSoon) {
                LinkCard(
                    title = stringResource(R.string.vpn360_website),
                    subtitle = if (website != null) website.removePrefix("https://") + " · " +
                        stringResource(R.string.vpn360_website_desc) else stringResource(R.string.vpn360_soon),
                    icon = R.drawable.ic_promotion_24dp,
                    enabled = website != null,
                    onClick = onWebsite
                )
            }
            if (hasSupport) {
                LinkCard(
                    title = stringResource(R.string.vpn360_menu_support),
                    subtitle = stringResource(R.string.vpn360_support_desc),
                    icon = R.drawable.ic_feedback_24dp,
                    onClick = onSupport
                )
            }
            LinkCard(
                title = stringResource(R.string.vpn360_privacy),
                subtitle = null,
                icon = R.drawable.ic_privacy_24dp,
                onClick = onPrivacy
            )
            LinkCard(
                title = stringResource(R.string.vpn360_open_source),
                subtitle = null,
                icon = R.drawable.ic_source_code_24dp,
                onClick = onSource
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun LinkCard(title: String, subtitle: String?, icon: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                painterResource(R.drawable.ic_arrow_back_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(180f)
            )
        }
    }
}
