package com.v2ray.ang.ui.vpn360

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * VPN 360 start screen: plans, student plan, step-by-step help and account activation.
 *
 * The "web" build (direct download) shows prices and plan buttons. The Play Store build
 * (SHOW_PLANS = false) only shows activation and help, as Google Play requires.
 * Results go back to MainActivity, which performs the import so its lists refresh.
 */
class WelcomeActivity : BaseComponentActivity() {

    companion object {
        const val EXTRA_ACTION = "vpn360_action"
        const val EXTRA_CONFIG = "vpn360_config"
        const val ACTION_IMPORT = "import"
        const val ACTION_SCAN_QR = "scan_qr"
    }

    private val canBuy: Boolean
        get() = BuildConfig.CENTRAL_URL.isNotEmpty() || BuildConfig.SALES_WHATSAPP.isNotEmpty()

    @Composable
    override fun ScreenContent() {
        WelcomeScreen(
            showPlans = BuildConfig.SHOW_PLANS,
            canBuy = canBuy,
            onChoosePlan = { openPlan(it) },
            onScanQr = { finishWith(ACTION_SCAN_QR, null) },
            onActivated = { url -> finishWith(ACTION_IMPORT, url) },
            onClose = { finish() },
            onSupport = if (BuildConfig.SALES_WHATSAPP.isNotEmpty()) {
                { openWhatsApp("Hola, necesito ayuda con VPN 360") }
            } else {
                null
            },
            login = { email, code -> login(email, code) }
        )
    }

    private fun openPlan(plan: Vpn360Plan) {
        when {
            BuildConfig.CENTRAL_URL.isNotEmpty() ->
                openUrl(BuildConfig.CENTRAL_URL.trimEnd('/') + "/?plan=" + plan.id)

            BuildConfig.SALES_WHATSAPP.isNotEmpty() ->
                openWhatsApp("Hola, quiero el plan ${plan.title} (${plan.perMonth} al mes) de VPN 360")
        }
    }

    private fun openWhatsApp(text: String) {
        openUrl("https://wa.me/" + BuildConfig.SALES_WHATSAPP + "?text=" + Uri.encode(text))
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // No browser / WhatsApp available: nothing else to do
        }
    }

    private fun finishWith(action: String, config: String?) {
        val data = Intent().putExtra(EXTRA_ACTION, action)
        if (config != null) data.putExtra(EXTRA_CONFIG, config)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private suspend fun login(email: String, code: String): LoginResult = withContext(Dispatchers.IO) {
        if (BuildConfig.CENTRAL_URL.isEmpty()) {
            return@withContext LoginResult(
                error = "La activación con correo estará disponible muy pronto. " +
                        "Mientras tanto usa «Escanear código QR» con el código que te enviamos."
            )
        }
        try {
            val url = URL(BuildConfig.CENTRAL_URL.trimEnd('/') + "/api/app/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
            body.put("email", email.trim())
            body.put("code", code.trim())
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                null
            }
            when {
                json == null -> LoginResult(error = "No se pudo conectar. Revisa tu internet e inténtalo de nuevo.")
                !json.optBoolean("ok") -> LoginResult(
                    error = json.optString("error").ifEmpty { "Correo o código incorrectos." }
                )

                !json.optBoolean("active") -> LoginResult(
                    error = "Tu suscripción no está activa. Renuévala o escríbenos por WhatsApp."
                )

                json.optString("subscription_url").isEmpty() -> LoginResult(error = "Respuesta inválida del servidor.")
                else -> LoginResult(url = json.optString("subscription_url"))
            }
        } catch (e: Exception) {
            LoginResult(error = "No se pudo conectar con el servidor de cuentas. Inténtalo de nuevo en unos minutos.")
        }
    }
}

data class Vpn360Plan(
    val id: String,
    val title: String,
    val perMonth: String,
    val detail: String,
    val badge: String? = null
)

data class LoginResult(val url: String? = null, val error: String? = null)

private val generalPlans = listOf(
    Vpn360Plan("mensual", "Mensual", "S/ 19.90", "Se cobra cada mes"),
    Vpn360Plan("semestral", "6 meses", "S/ 15.90", "S/ 95.40 cada 6 meses", "Ahorra 20%"),
    Vpn360Plan("anual", "Anual", "S/ 10.90", "S/ 130.80 al año", "Mejor precio · ahorra 45%")
)

private val studentPlan = Vpn360Plan(
    "estudiante",
    "Estudiante Internacional",
    "S/ 14.90",
    "Se cobra cada mes · solo estudiantes registrados de nuestro programa",
    "Exclusivo"
)

@Composable
private fun WelcomeScreen(
    showPlans: Boolean,
    canBuy: Boolean,
    onChoosePlan: (Vpn360Plan) -> Unit,
    onScanQr: () -> Unit,
    onActivated: (String) -> Unit,
    onClose: () -> Unit,
    onSupport: (() -> Unit)?,
    login: suspend (String, String) -> LoginResult
) {
    var showLogin by remember { mutableStateOf(false) }
    val red = MaterialTheme.colorScheme.primary
    val blue = MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Red header with the logo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(red)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(124.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("VPN 360", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Internet sin restricciones, estés donde estés",
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Cómo usar VPN 360")
            val steps = if (showPlans) {
                listOf(
                    "Elige tu plan aquí abajo y realiza el pago.",
                    "Activa tu cuenta: toca «Activar mi cuenta» con tu correo y código, o escanea el código QR que te enviamos.",
                    "Toca el botón redondo ▶ de la pantalla principal y acepta el permiso de VPN.",
                    "¡Listo! Verás «Conectado». Ya puedes usar WhatsApp, Instagram, YouTube y Google."
                )
            } else {
                listOf(
                    "Activa tu cuenta: toca «Activar mi cuenta» con tu correo y código, o escanea el código QR que recibiste.",
                    "Toca el botón redondo ▶ de la pantalla principal y acepta el permiso de VPN.",
                    "¡Listo! Verás «Conectado». Ya puedes usar WhatsApp, Instagram, YouTube y Google."
                )
            }
            steps.forEachIndexed { index, text -> StepBubble(index + 1, text, blue) }

            Button(
                onClick = { showLogin = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = red, contentColor = Color.White)
            ) {
                Text("Activar mi cuenta", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth()) {
                Text("Escanear código QR", color = blue)
            }

            if (showPlans) {
                Spacer(Modifier.height(4.dp))
                SectionTitle("Planes")
                generalPlans.forEach { plan ->
                    PlanCard(plan, canBuy, highlight = plan.id == "anual", accent = blue) { onChoosePlan(plan) }
                }
                Spacer(Modifier.height(4.dp))
                SectionTitle("Plan para estudiantes")
                PlanCard(studentPlan, canBuy, highlight = false, accent = blue) { onChoosePlan(studentPlan) }
                Text(
                    "Para el plan de estudiante usa el mismo WhatsApp o correo con el que te registraste en nuestro programa.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!canBuy) {
                    Text(
                        "Los pagos estarán disponibles muy pronto.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (onSupport != null) {
                TextButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) {
                    Text("¿Necesitas ayuda? Escríbenos por WhatsApp", color = blue)
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Ir a la pantalla principal")
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (showLogin) {
        LoginDialog(
            onDismiss = { showLogin = false },
            login = login,
            onSuccess = { url ->
                showLogin = false
                onActivated(url)
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun StepBubble(number: Int, text: String, accent: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text,
                modifier = Modifier.padding(12.dp),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: Vpn360Plan,
    enabled: Boolean,
    highlight: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val red = MaterialTheme.colorScheme.primary
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (highlight) 2.dp else 1.dp,
            if (highlight) red else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plan.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                if (plan.badge != null) {
                    Surface(shape = RoundedCornerShape(50), color = accent) {
                        Text(
                            plan.badge,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(plan.perMonth, color = red, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    " /mes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Text(plan.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = red, contentColor = Color.White)
            ) {
                Text(if (enabled) "Elegir este plan" else "Muy pronto")
            }
        }
    }
}

@Composable
private fun LoginDialog(
    onDismiss: () -> Unit,
    login: suspend (String, String) -> LoginResult,
    onSuccess: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Activar mi cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Escribe el correo con el que pagaste y tu código de acceso.", fontSize = 14.sp)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Código de acceso") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                val message = error
                if (message != null) {
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && email.isNotBlank() && code.isNotBlank(),
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        val result = login(email, code)
                        loading = false
                        val url = result.url
                        if (url != null) onSuccess(url) else error = result.error
                    }
                }
            ) {
                Text("Activar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
        }
    )
}
