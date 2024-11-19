package com.yubico.yubikit_fido_demo.webhandler.webhooks

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.yubico.yubikit_fido_demo.MainActivity
import com.yubico.yubikit_fido_demo.webhandler.PasskeyWebListener
import com.yubico.yubikit_fido_demo.yubikit.YubiKitWebAuthnHandler
import kotlinx.coroutines.CoroutineScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("hookWebAuthnWithListener")

/**
 * Connects the local app logic with the web page via injection of javascript through a
 * WebListener. Handles ensuring the [PasskeyWebListener] is hooked up to the webView page
 * if compatible.
 */
fun hookWebAuthnWithListener(
    webView: WebView,
    activity: MainActivity,
    coroutineScope: CoroutineScope,
    yubiKitWebAuthnHandler: YubiKitWebAuthnHandler
) {

    val passkeyWebListener = PasskeyWebListener(activity, coroutineScope, yubiKitWebAuthnHandler)
    val webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            logger.trace("onPageStarted: {}", url)
            logger.trace("userAgent: {}", view?.settings?.userAgentString)
            passkeyWebListener.onPageStarted();
            webView.evaluateJavascript(PasskeyWebListener.INJECTED_VAL, null)
        }

        /**
         * Updates the title in the App Bar.
         */
        override fun onPageFinished(view: WebView, url: String) {
            logger.trace("onPageFinished: {}", url)
            super.onPageFinished(view, url)
        }
    }

    val rules = setOf("*")
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(
            webView, PasskeyWebListener.INTERFACE_NAME,
            rules, passkeyWebListener
        )
    }

    webView.webViewClient = webViewClient
}