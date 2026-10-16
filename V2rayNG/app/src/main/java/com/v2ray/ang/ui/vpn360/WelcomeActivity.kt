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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
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
        const val PREF_ONBOARDED = "vpn360_onboarded"
    }

    @Composable
    override fun ScreenContent() {
        val canBuy = BuildConfig.CENTRAL_URL.isNotEmpty() || BuildConfig.SALES_WHATSAPP.isNotEmpty()
        WelcomeScreen(
            showPlans = BuildConfig.SHOW_PLANS,
            canBuy = canBuy,
            onChoosePlan = { planName, planId -> openPlan(planName, planId) },
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

    private fun openPlan(planName: String, planId: String) {
        when {
            BuildConfig.CENTRAL_URL.isNotEmpty() ->
                openUrl(BuildConfig.CENTRAL_URL.trimEnd('/') + "/?plan=" + planId)

            BuildConfig.SALES_WHATSAPP.isNotEmpty() ->
                openUrl(
                    "https://wa.me/" + BuildConfig.SALES_WHATSAPP + "?text=" +
                        Uri.encode(getString(R.string.vpn360_whatsapp_plan_msg, planName))
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
    onChoosePlan: (String, String) -> Unit,
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
                PlansPage(canBuy = canBuy, onChoosePlan = onChoosePlan)
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

private data class PlanUi(val id: String, val name: Int, val price: String, val bill: Int, val badge: Int?, val highlight: Boolean)

private val generalPlans = listOf(
    PlanUi("mensual", R.string.vpn360_plan_monthly, "S/ 19.90", R.string.vpn360_bill_monthly, null, false),
    PlanUi("semestral", R.string.vpn360_plan_6m, "S/ 15.90", R.string.vpn360_bill_6m, R.string.vpn360_save_20, false),
    PlanUi("anual", R.string.vpn360_plan_annual, "S/ 10.90", R.string.vpn360_bill_annual, R.string.vpn360_save_45, true)
)

private val studentPlan =
    PlanUi("estudiante", R.string.vpn360_plan_student, "S/ 14.90", R.string.vpn360_bill_student, R.string.vpn360_exclusive, false)

@Composable
private fun PlansPage(canBuy: Boolean, onChoosePlan: (String, String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.vpn360_plans_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        generalPlans.forEach { plan -> PlanCard(plan, canBuy, onChoosePlan) }
        Text(
            stringResource(R.string.vpn360_student_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onBackground
        )
        PlanCard(studentPlan, canBuy, onChoosePlan)
        Text(
            stringResource(R.string.vpn360_student_note),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PlanCard(plan: PlanUi, canBuy: Boolean, onChoosePlan: (String, String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val name = stringResource(plan.name)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (plan.highlight) 2.dp else 1.dp,
            if (plan.highlight) primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (plan.badge != null) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiary) {
                        Text(
                            stringResource(plan.badge),
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(plan.price, color = primary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    stringResource(R.string.vpn360_per_month),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Text(stringResource(plan.bill), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onChoosePlan(name, plan.id) },
                enabled = canBuy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text(stringResource(if (canBuy) R.string.vpn360_choose_plan else R.string.vpn360_soon))
            }
        }
    }
}
