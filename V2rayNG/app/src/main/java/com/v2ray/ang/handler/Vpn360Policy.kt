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

    private val serverHosts: Set<String> by lazy {
        BuildConfig.VPN360_SERVER_HOSTS.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    /**
     * vless://…?security=reality&pbk=<one of our keys>…, or
     * hysteria2://…@<one of our servers>…?obfs=salamander&obfs-password=…&pinSHA256=… (UDP route)
     */
    fun isOurServerLink(link: String): Boolean {
        val l = link.trim()
        return try {
            when {
                l.startsWith("vless://", ignoreCase = true) -> {
                    val uri = Uri.parse(l)
                    uri.getQueryParameter("security").equals("reality", ignoreCase = true) &&
                        uri.getQueryParameter("pbk")?.trim() in realityKeys
                }

                l.startsWith("hysteria2://", ignoreCase = true) || l.startsWith("hy2://", ignoreCase = true) -> {
                    val uri = Uri.parse(l)
                    uri.host?.lowercase() in serverHosts &&
                        uri.getQueryParameter("obfs").equals("salamander", ignoreCase = true) &&
                        !uri.getQueryParameter("obfs-password").isNullOrBlank() &&
                        !uri.getQueryParameter("pinSHA256").isNullOrBlank()
                }

                else -> false
            }
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
        // A single link split by line breaks when copied from a terminal or a chat
        val joined = text.filterNot { it.isWhitespace() }
        if (joined.contains("://") && isAllowedLine(joined)) return joined
        val decoded = Utils.decode(text.trim())
        val fromBase64 = decoded.lines().map { it.trim() }.filter { it.isNotEmpty() && isAllowedLine(it) }
        return if (fromBase64.isNotEmpty()) fromBase64.joinToString("\n") else null
    }
}
