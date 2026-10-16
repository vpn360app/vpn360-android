package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.vpn360.HomeActivity
import com.v2ray.ang.util.LogUtil
import java.net.URLDecoder

/**
 * Receives vpn360://install-sub?url=… (and shared text) and hands the link to the VPN 360 home,
 * which imports it while showing progress.
 */
class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var toImport: String? = null
        try {
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        // Shared text is the link itself (e.g. a vless:// link shared from WhatsApp): keep it as is
                        toImport = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    when (data?.host) {
                        "install-config", "install-sub" -> {
                            val uri: Uri? = intent.data
                            toImport = buildImport(uri?.getQueryParameter("url").orEmpty(), uri?.fragment)
                        }

                        else -> toastError(R.string.toast_failure)
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
        }

        val home = Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!toImport.isNullOrBlank()) home.putExtra(HomeActivity.EXTRA_IMPORT, toImport)
        startActivity(home)
        finish()
    }

    @Composable
    override fun ScreenContent() {
    }

    private fun buildImport(uriString: String?, fragment: String?): String? {
        if (uriString.isNullOrEmpty()) {
            return null
        }
        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null && uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
            decodedUrl += "#${fragment}"
        }
        LogUtil.i(AppConfig.TAG, "VPN 360 link received")
        return decodedUrl
    }
}
