package com.v2ray.ang.ui.vpn360

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.Vpn360Policy
import com.v2ray.ang.util.LogUtil
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Calls to the VPN 360 account server (CENTRAL_URL). All functions block: call them on
 * Dispatchers.IO. Nothing secret lives in the app; the server holds the Mercado Pago and
 * panel keys.
 */
object Vpn360Api {

    const val PREF_PENDING_REF = "vpn360_pending_ref"
    const val PREF_PENDING_AT = "vpn360_pending_at"
    const val PREF_BUYER_NAME = "vpn360_buyer_name"
    const val PREF_BUYER_EMAIL = "vpn360_buyer_email"
    const val PREF_BUYER_WHATS = "vpn360_buyer_whats"

    val enabled: Boolean get() = BuildConfig.CENTRAL_URL.startsWith("https://")

    private val base: String get() = BuildConfig.CENTRAL_URL.trim().trimEnd('/')

    private data class Response(val status: Int, val json: JSONObject?)

    private fun call(method: String, path: String, body: JSONObject? = null): Response {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Response(status, runCatching { JSONObject(text) }.getOrNull())
        } finally {
            conn.disconnect()
        }
    }

    // ---- access code ----

    data class LoginResult(val subUrl: String? = null, val error: Int? = null)

    fun login(code: String): LoginResult {
        if (!enabled) return LoginResult(error = R.string.vpn360_error_soon)
        return try {
            val r = call("POST", "/api/app/login", JSONObject().put("code", code))
            val json = r.json
            when {
                r.status == 401 || r.status == 404 -> LoginResult(error = R.string.vpn360_error_wrong_code)
                r.status !in 200..299 || json == null -> LoginResult(error = R.string.vpn360_error_offline)
                !json.optBoolean("active") -> LoginResult(error = R.string.vpn360_error_inactive)
                Vpn360Policy.isOurSubscription(json.optString("subscription_url")) ->
                    LoginResult(subUrl = json.optString("subscription_url"))

                else -> LoginResult(error = R.string.vpn360_error_offline)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "VPN 360 login failed", e)
            LoginResult(error = R.string.vpn360_error_offline)
        }
    }

    // ---- buying a plan ----

    /** [error] is a message already translated by the server; [errorRes] a local one. */
    data class SubscribeResult(
        val ref: String? = null,
        val checkoutUrl: String? = null,
        val error: String? = null,
        val errorRes: Int? = null
    )

    fun subscribe(name: String, email: String, whatsapp: String, plan: String, lang: String): SubscribeResult {
        if (!enabled) return SubscribeResult(errorRes = R.string.vpn360_error_soon)
        return try {
            val body = JSONObject()
                .put("name", name).put("email", email).put("whatsapp", whatsapp)
                .put("plan", plan).put("lang", lang).put("accept", true)
            val r = call("POST", "/api/app/subscribe", body)
            val json = r.json
            val checkout = json?.optString("checkout_url").orEmpty()
            when {
                r.status in 200..299 && checkout.startsWith("https://") ->
                    SubscribeResult(ref = json?.optString("ref"), checkoutUrl = checkout)

                json != null && json.optString("error").isNotEmpty() -> SubscribeResult(error = json.optString("error"))
                else -> SubscribeResult(errorRes = R.string.vpn360_error_offline)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "VPN 360 subscribe failed", e)
            SubscribeResult(errorRes = R.string.vpn360_error_offline)
        }
    }

    data class OrderResult(val known: Boolean, val active: Boolean, val subUrl: String?, val failed: Boolean = false)

    fun order(ref: String): OrderResult {
        if (!enabled) return OrderResult(known = false, active = false, subUrl = null, failed = true)
        return try {
            val r = call("GET", "/api/app/order/" + URLEncoder.encode(ref, "UTF-8"))
            val json = r.json
            when {
                r.status == 404 -> OrderResult(known = false, active = false, subUrl = null)
                r.status !in 200..299 || json == null -> OrderResult(known = true, active = false, subUrl = null, failed = true)
                else -> {
                    val sub = json.optString("subscription_url")
                    OrderResult(
                        known = true,
                        active = json.optBoolean("active") && Vpn360Policy.isOurSubscription(sub),
                        subUrl = sub.takeIf { Vpn360Policy.isOurSubscription(it) }
                    )
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "VPN 360 order check failed", e)
            OrderResult(known = true, active = false, subUrl = null, failed = true)
        }
    }

    // ---- account status (expiry) ----

    /** The token is the last part of our subscription link: CENTRAL_URL/s/<token>. */
    fun accountToken(): String? {
        val sub = MmkvManager.decodeSubscriptions().map { it.subscription.url }
            .firstOrNull { Vpn360Policy.isOurSubscription(it) } ?: return null
        return sub.substringAfter("/s/").substringBefore('?').substringBefore('#').takeIf { it.isNotBlank() }
    }

    /** null when unknown (offline, no account server, or a direct test link). */
    fun isAccountActive(): Boolean? {
        if (!enabled) return null
        val token = accountToken() ?: return null
        return try {
            val r = call("GET", "/api/app/status?token=" + URLEncoder.encode(token, "UTF-8"))
            when {
                r.status == 401 -> false
                r.status in 200..299 && r.json != null -> r.json.optBoolean("active")
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
