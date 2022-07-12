package com.duckduckgo.autoconsent.api

import android.webkit.WebView
import androidx.annotation.UiThread

interface Autoconsent {
    fun injectAutoconsent(webView: WebView)

    fun addJsInterface(webView: WebView)

    @UiThread
    fun init(webView: WebView)
}
