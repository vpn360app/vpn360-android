package com.v2ray.ang.ui.vpn360

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import kotlinx.coroutines.delay

/** Languages offered in VPN 360 ("auto" follows the phone). */
internal data class LangOption(val code: String, val native: String, val badge: String, val greeting: String)

internal val langOptions = listOf(
    LangOption("es", "Español", "ES", "¡Hola!"),
    LangOption("en", "English", "EN", "Hello!"),
    LangOption("ru", "Русский", "RU", "Привет!")
)

/** Bottom sheet with the languages; rows slide in one after another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageSheet(current: String, onDismiss: () -> Unit, onSelect: (LangOption) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val autoLabel = stringResource(R.string.vpn360_language_auto)
    val options = langOptions + LangOption("auto", autoLabel, "", "")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.vpn360_language_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            options.forEachIndexed { index, option ->
                LanguageRow(
                    option = option,
                    selected = option.code == current,
                    index = index,
                    onClick = { if (option.code == current) onDismiss() else onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(option: LangOption, selected: Boolean, index: Int, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * index)
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val border by animateColorAsState(
        if (selected) primary else MaterialTheme.colorScheme.outlineVariant, tween(250), label = "langBorder"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 40f
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = option.native
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (option.badge.isNotEmpty()) {
                Text(
                    option.badge,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val c = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(Modifier.size(20.dp)) {
                    val r = size.minDimension * 0.45f
                    val s = Stroke(width = size.minDimension * 0.09f)
                    drawCircle(c, r, style = s)
                    drawLine(c, Offset(center.x - r, center.y), Offset(center.x + r, center.y), s.width)
                    drawOval(c, Offset(center.x - r * 0.45f, center.y - r), Size(r * 0.9f, r * 2), style = s)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(option.native, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Canvas(Modifier.size(24.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.52f)
                    lineTo(size.width * 0.42f, size.height * 0.74f)
                    lineTo(size.width * 0.84f, size.height * 0.28f)
                }
                drawPath(p, primary, style = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * Full-screen curtain shown while the app switches language (the screen is rebuilt behind it):
 * it fades in with a greeting in the new language and fades out once the new texts are ready.
 */
@Composable
internal fun LanguageOverlay(visible: Boolean, label: String, greeting: String) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(260)), exit = fadeOut(tween(480))) {
        val primary = MaterialTheme.colorScheme.primary
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        val accent = MaterialTheme.colorScheme.tertiary
        val spin = rememberInfiniteTransition(label = "langSpin")
        val angle by spin.animateFloat(
            0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "angle"
        )
        val pop = remember { Animatable(0.6f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(primary)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.minDimension
                        val stroke = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                        drawCircle(onPrimary.copy(alpha = 0.25f), w * 0.44f, style = stroke)
                        drawArc(
                            accent, angle, 110f, false,
                            topLeft = Offset(w * 0.06f, w * 0.06f), size = Size(w * 0.88f, w * 0.88f), style = stroke
                        )
                    }
                    Text("360", color = onPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(28.dp))
                if (greeting.isNotEmpty()) {
                    Text(
                        greeting,
                        color = onPrimary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(label, color = onPrimary.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
