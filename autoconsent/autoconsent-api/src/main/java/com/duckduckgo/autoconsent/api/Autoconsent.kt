package com.duckduckgo.autoconsent.api

import android.webkit.WebView

interface Autoconsent {
    fun injectAutoconsent(webView: WebView)

    fun addJsInterface(webView: WebView)
}
