package com.v2ray.ang.ui.vpn360

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.launch

/**
 * VPN 360 welcome: swipeable pages with the benefits of the VPN and, in the direct-download
 * build (SHOW_PLANS), the plans. Google Play builds never show prices or payment buttons.
 * The result tells the caller what to do next (scan a code, or import an activation link).
 */
class WelcomeActivity : BaseComponentActivity() {

    companion object {
        const val EXTRA_ACTION = "vpn360_action"
        const val EXTRA_CONFIG = "vpn360_config"
        const val ACTION_IMPORT = "import"
        const val ACTION_SCAN_QR = "scan_qr"
        const val ACTION_BUY = "buy"
        const val EXTRA_PLAN = "vpn360_plan"
        const val PREF_ONBOARDED = "vpn360_onboarded"
    }

    @Composable
    override fun ScreenContent() {
        val canBuy = Vpn360Api.enabled || BuildConfig.SALES_WHATSAPP.isNotEmpty()
        WelcomeScreen(
            showPlans = BuildConfig.SHOW_PLANS,
            canBuy = canBuy,
            onChoosePlan = { plan -> openPlan(plan) },
            onFinish = { scan -> finishWelcome(scan) }
        )
    }

    private fun finishWelcome(scan: Boolean) {
        MmkvManager.encodeSettings(PREF_ONBOARDED, true)
        if (scan) {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ACTION, ACTION_SCAN_QR))
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    /** Buying happens inside the app (BuyActivity, opened by the home); WhatsApp as a fallback. */
    private fun openPlan(plan: PlanUi) {
        when {
            Vpn360Api.enabled -> {
                MmkvManager.encodeSettings(PREF_ONBOARDED, true)
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ACTION, ACTION_BUY).putExtra(EXTRA_PLAN, plan.id))
                finish()
            }

            BuildConfig.SALES_WHATSAPP.isNotEmpty() ->
                openUrl(
                    "https://wa.me/" + BuildConfig.SALES_WHATSAPP + "?text=" +
                        Uri.encode(getString(R.string.vpn360_whatsapp_plan_msg, getString(plan.name)))
                )
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // No browser or WhatsApp installed
        }
    }
}

private enum class BenefitIcon { Globe, Chat, Lock, Power }

private data class Benefit(val icon: BenefitIcon, val title: Int, val text: Int)

private val benefits = listOf(
    Benefit(BenefitIcon.Globe, R.string.vpn360_ob1_title, R.string.vpn360_ob1_text),
    Benefit(BenefitIcon.Chat, R.string.vpn360_ob2_title, R.string.vpn360_ob2_text),
    Benefit(BenefitIcon.Lock, R.string.vpn360_ob3_title, R.string.vpn360_ob3_text),
    Benefit(BenefitIcon.Power, R.string.vpn360_ob4_title, R.string.vpn360_ob4_text)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WelcomeScreen(
    showPlans: Boolean,
    canBuy: Boolean,
    onChoosePlan: (PlanUi) -> Unit,
    onFinish: (Boolean) -> Unit
) {
    val pageCount = benefits.size + if (showPlans) 1 else 0
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pageCount - 1
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
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { onFinish(false) }) {
                Text(stringResource(R.string.vpn360_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            if (page < benefits.size) {
                BenefitPage(benefits[page])
            } else {
                PlansList(canBuy = canBuy, onChoosePlan = onChoosePlan, modifier = Modifier.fillMaxSize())
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until pageCount) {
                val selected = i == pagerState.currentPage
                val width by animateDpAsState(if (selected) 26.dp else 8.dp, tween(300), label = "dot")
                val color by animateColorAsState(
                    if (selected) primary else MaterialTheme.colorScheme.outlineVariant,
                    tween(300),
                    label = "dotColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    if (isLast) {
                        onFinish(false)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text(
                    stringResource(if (isLast) R.string.vpn360_start else R.string.vpn360_next),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = { onFinish(true) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vpn360_have_account), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun BenefitPage(benefit: Benefit) {
    val transition = rememberInfiniteTransition(label = "float")
    val offset by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "offset"
    )
    val glow by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer { translationY = offset * density },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = glow))
            )
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(primary),
                contentAlignment = Alignment.Center
            ) {
                val iconColor = MaterialTheme.colorScheme.onPrimary
                Canvas(modifier = Modifier.size(84.dp)) { drawBenefitIcon(benefit.icon, iconColor) }
            }
        }
        Spacer(Modifier.height(36.dp))
        Text(
            stringResource(benefit.title),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(benefit.text),
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.drawBenefitIcon(icon: BenefitIcon, color: Color) {
    val w = size.minDimension
    val stroke = Stroke(width = w * 0.075f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    val c = center
    when (icon) {
        BenefitIcon.Globe -> {
            val r = w * 0.42f
            drawCircle(color, radius = r, center = c, style = stroke)
            drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = stroke.width)
            drawOval(color, topLeft = Offset(c.x - r * 0.45f, c.y - r), size = Size(r * 0.9f, r * 2f), style = stroke)
        }

        BenefitIcon.Chat -> {
            val l = w * 0.1f
            val t = w * 0.14f
            drawRoundRect(
                color, topLeft = Offset(l, t), size = Size(w * 0.8f, w * 0.56f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f), style = stroke
            )
            drawLine(color, Offset(w * 0.3f, w * 0.7f), Offset(w * 0.22f, w * 0.88f), strokeWidth = stroke.width, cap = stroke.cap)
            for (i in 0..2) drawCircle(color, radius = w * 0.05f, center = Offset(w * (0.33f + i * 0.17f), w * 0.42f))
        }

        BenefitIcon.Lock -> {
            drawRoundRect(
                color, topLeft = Offset(w * 0.18f, w * 0.44f), size = Size(w * 0.64f, w * 0.46f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = stroke
            )
            drawArc(
                color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(w * 0.3f, w * 0.14f), size = Size(w * 0.4f, w * 0.6f), style = stroke
            )
            drawCircle(color, radius = w * 0.06f, center = Offset(c.x, w * 0.66f))
        }

        BenefitIcon.Power -> {
            val r = w * 0.36f
            drawArc(
                color, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r + w * 0.05f), size = Size(r * 2, r * 2), style = stroke
            )
            drawLine(color, Offset(c.x, w * 0.08f), Offset(c.x, w * 0.46f), strokeWidth = stroke.width, cap = stroke.cap)
        }
    }
}
