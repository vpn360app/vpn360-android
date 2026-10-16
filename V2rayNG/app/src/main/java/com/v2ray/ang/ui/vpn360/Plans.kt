package com.v2ray.ang.ui.vpn360

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import org.json.JSONObject
import java.util.Locale

/**
 * A VPN 360 plan. Mercado Pago charges [amount] soles every [months] months; the dollar price is
 * a reference computed with the exchange rate published by the account server.
 */
data class PlanUi(
    val id: String,
    val name: Int,
    val months: Int,
    val amount: Double,
    val perMonth: Double,
    val badge: Int?,
    val highlight: Boolean,
    val student: Boolean = false
)

private val builtInPlans = listOf(
    PlanUi("mensual", R.string.vpn360_plan_monthly, 1, 19.90, 19.90, null, false),
    PlanUi("semestral", R.string.vpn360_plan_6m, 6, 95.40, 15.90, R.string.vpn360_save_20, false),
    PlanUi("anual", R.string.vpn360_plan_annual, 12, 130.80, 10.90, R.string.vpn360_save_45, true),
    PlanUi("estudiante", R.string.vpn360_plan_student, 1, 14.90, 14.90, R.string.vpn360_exclusive, false, student = true)
)

/**
 * Prices and exchange rate: built in, replaced by what the account server publishes
 * (/api/app/plans, cached in MMKV) so prices change without a new app release.
 */
internal object PriceBook {
    const val PREF_RATE = "vpn360_pen_per_usd"
    const val PREF_PLANS = "vpn360_plans_json"
    private const val DEFAULT_RATE = 3.45

    val rate: Double
        get() = MmkvManager.decodeSettingsString(PREF_RATE)?.toDoubleOrNull()?.takeIf { it in 2.5..6.0 } ?: DEFAULT_RATE

    val plans: List<PlanUi>
        get() {
            val raw = MmkvManager.decodeSettingsString(PREF_PLANS).orEmpty()
            if (raw.isEmpty()) return builtInPlans
            return try {
                val list = JSONObject(raw).getJSONArray("plans")
                val byId = (0 until list.length()).map { list.getJSONObject(it) }.associateBy { it.optString("id") }
                builtInPlans.map { p ->
                    val s = byId[p.id] ?: return@map p
                    p.copy(
                        months = s.optInt("months", p.months),
                        amount = s.optDouble("amount", p.amount),
                        perMonth = s.optDouble("per_month", p.perMonth)
                    )
                }
            } catch (e: Exception) {
                builtInPlans
            }
        }

    /** Saves the server's answer ({"pen_per_usd":…, "plans":[…]}). */
    fun save(json: String) {
        try {
            val o = JSONObject(json)
            val rate = o.optDouble("pen_per_usd", 0.0)
            if (rate in 2.5..6.0) MmkvManager.encodeSettings(PREF_RATE, rate.toString())
            if (o.optJSONArray("plans") != null) MmkvManager.encodeSettings(PREF_PLANS, json)
        } catch (e: Exception) {
            // keep the previous prices
        }
    }
}

internal fun planById(id: String?, plans: List<PlanUi> = PriceBook.plans): PlanUi? = plans.firstOrNull { it.id == id }

internal fun usd(pen: Double, rate: Double): String = String.format(Locale.US, "US$ %.2f", pen / rate)

internal fun pen(amount: Double): String = String.format(Locale.US, "S/ %.2f", amount)

/** "Se cobra cada mes", "US$ 27.65 cada 6 meses (S/ 95.40)", "US$ 37.91 al año (S/ 130.80)". */
@Composable
internal fun billingLine(plan: PlanUi, rate: Double): String = when {
    plan.student -> stringResource(R.string.vpn360_bill_student)
    plan.months == 1 -> stringResource(R.string.vpn360_bill_monthly)
    plan.months == 12 -> stringResource(R.string.vpn360_bill_year_fmt, usd(plan.amount, rate), pen(plan.amount))
    else -> stringResource(R.string.vpn360_bill_6m_fmt, usd(plan.amount, rate), pen(plan.amount))
}

/** The VPN 360 plans with a button each; used by the welcome pages, "Mi plan" and the purchase. */
@Composable
internal fun PlansList(
    canBuy: Boolean,
    onChoosePlan: (PlanUi) -> Unit,
    modifier: Modifier = Modifier,
    plans: List<PlanUi>,
    rate: Double,
    currentPlanId: String? = null,
    showTitle: Boolean = true,
    scrollable: Boolean = true
) {
    Column(
        modifier = (if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showTitle) {
            Text(
                stringResource(R.string.vpn360_plans_title),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        plans.filter { !it.student }.forEach { plan -> PlanCard(plan, rate, canBuy, plan.id == currentPlanId, onChoosePlan) }
        Text(
            stringResource(R.string.vpn360_student_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onBackground
        )
        plans.filter { it.student }.forEach { plan -> PlanCard(plan, rate, canBuy, plan.id == currentPlanId, onChoosePlan) }
        Text(
            stringResource(R.string.vpn360_student_note),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.vpn360_dollar_note),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PlanCard(plan: PlanUi, rate: Double, canBuy: Boolean, isCurrent: Boolean, onChoosePlan: (PlanUi) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val name = stringResource(plan.name)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (plan.highlight || isCurrent) 2.dp else 1.dp,
            when {
                isCurrent -> MaterialTheme.colorScheme.tertiary
                plan.highlight -> primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                val badge = if (isCurrent) R.string.vpn360_your_plan else plan.badge
                if (badge != null) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiary) {
                        Text(
                            stringResource(badge),
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(usd(plan.perMonth, rate), color = primary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(2.dp))
                Text(
                    stringResource(R.string.vpn360_per_month),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Text(
                stringResource(R.string.vpn360_pen_note, pen(plan.perMonth)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(billingLine(plan, rate), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            if (!isCurrent) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onChoosePlan(plan) },
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
}
