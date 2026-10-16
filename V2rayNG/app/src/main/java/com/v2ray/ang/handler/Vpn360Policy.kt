package com.v2ray.ang.handler

import android.net.Uri
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.Utils

/**
 * VPN 360 only works with VPN 360 accounts: a vless REALITY link signed by one of our server
 * keys, or a subscription served by our account server (CENTRAL_URL/s/…). Links from other VPN
 * providers, other protocols and raw JSON configs are refused. The real protection is on the
 * server (expiry dates and device limits); this keeps the app from being used as a generic client.
 */
object Vpn360Policy {

    private val realityKeys: Set<String> by lazy {
        BuildConfig.VPN360_REALITY_KEYS.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** https://<CENTRAL_URL>/s/<id> */
    fun isOurSubscription(url: String): Boolean {
        val base = BuildConfig.CENTRAL_URL.trim().trimEnd('/')
        if (base.isEmpty() || !base.startsWith("https://")) return false
        val u = url.trim()
        return u.startsWith("$base/s/") && u.length > base.length + 3 && !u.contains(' ')
    }

    /** vless://…?security=reality&pbk=<one of our keys>… */
    fun isOurServerLink(link: String): Boolean {
        val l = link.trim()
        if (!l.startsWith("vless://", ignoreCase = true)) return false
        return try {
            val uri = Uri.parse(l)
            uri.getQueryParameter("security").equals("reality", ignoreCase = true) &&
                uri.getQueryParameter("pbk")?.trim() in realityKeys
        } catch (e: Exception) {
            false
        }
    }

    fun isAllowedLine(line: String): Boolean = isOurServerLink(line) || isOurSubscription(line)

    /**
     * Keeps only VPN 360 links from an import (plain text, one link per line, or base64 of that).
     * Returns null when nothing is allowed.
     */
    fun filter(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val direct = text.lines().map { it.trim() }.filter { it.isNotEmpty() && isAllowedLine(it) }
        if (direct.isNotEmpty()) return direct.joinToString("\n")
        val decoded = Utils.decode(text.trim())
        val fromBase64 = decoded.lines().map { it.trim() }.filter { it.isNotEmpty() && isAllowedLine(it) }
        return if (fromBase64.isNotEmpty()) fromBase64.joinToString("\n") else null
    }
}
