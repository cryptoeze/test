package com.cryptoeze.app.web

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/** WebView that knows whether the page (or an inner scroll area) is scrolled, for pull-to-refresh. */
class AppWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : WebView(context, attrs) {

    /** Set from the page script on every touch; true when an inner container is scrolled down. */
    @Volatile
    var innerScrolled = false

    val canPullToRefresh: Boolean
        get() = scrollY == 0 && !innerScrolled
}
