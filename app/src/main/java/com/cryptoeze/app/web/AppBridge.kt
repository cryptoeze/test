package com.cryptoeze.app.web

import android.webkit.JavascriptInterface
import com.cryptoeze.app.BuildConfig

/**
 * Exposed to the website as `window.CryptoEzeApp`. The website can detect the app and
 * register the device for push notifications with `CryptoEzeApp.getFcmToken()`.
 */
class AppBridge(private val host: Host) {

    interface Host {
        fun fcmToken(): String
        fun copyText(text: String)
        fun saveBase64File(dataUrl: String, fileName: String, mimeType: String)
        fun setInnerScrolled(scrolled: Boolean)
    }

    @JavascriptInterface
    fun isApp(): Boolean = true

    @JavascriptInterface
    fun getPlatform(): String = "android"

    @JavascriptInterface
    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun getFcmToken(): String = host.fcmToken()

    @JavascriptInterface
    fun copyText(text: String?) {
        if (!text.isNullOrEmpty()) host.copyText(text)
    }

    @JavascriptInterface
    fun saveBase64(dataUrl: String?, fileName: String?, mimeType: String?) {
        if (dataUrl.isNullOrEmpty()) return
        host.saveBase64File(dataUrl, fileName.orEmpty(), mimeType.orEmpty())
    }

    @JavascriptInterface
    fun setInnerScrolled(scrolled: Boolean) = host.setInnerScrolled(scrolled)

    companion object {
        const val NAME = "CryptoEzeApp"
    }
}
