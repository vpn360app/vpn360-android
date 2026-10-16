package com.v2ray.ang.ui.vpn360

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/** Prices shown in the app; the account server charges what its own plan list says. */
internal data class PlanUi(val id: String, val name: Int, val price: String, val bill: Int, val badge: Int?, val highlight: Boolean)

internal val generalPlans = listOf(
    PlanUi("mensual", R.string.vpn360_plan_monthly, "S/ 19.90", R.string.vpn360_bill_monthly, null, false),
    PlanUi("semestral", R.string.vpn360_plan_6m, "S/ 15.90", R.string.vpn360_bill_6m, R.string.vpn360_save_20, false),
    PlanUi("anual", R.string.vpn360_plan_annual, "S/ 10.90", R.string.vpn360_bill_annual, R.string.vpn360_save_45, true)
)

internal val studentPlan =
    PlanUi("estudiante", R.string.vpn360_plan_student, "S/ 14.90", R.string.vpn360_bill_student, R.string.vpn360_exclusive, false)

internal fun planById(id: String?): PlanUi? = (generalPlans + studentPlan).firstOrNull { it.id == id }

/** The VPN 360 plans with a button each; used by the welcome pages and the purchase screen. */
@Composable
internal fun PlansList(canBuy: Boolean, onChoosePlan: (PlanUi) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
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
private fun PlanCard(plan: PlanUi, canBuy: Boolean, onChoosePlan: (PlanUi) -> Unit) {
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
