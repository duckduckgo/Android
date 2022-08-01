package com.duckduckgo.autoconsent.api

import android.webkit.WebView
import androidx.fragment.app.DialogFragment

interface Autoconsent {
    fun injectAutoconsent(webView: WebView)
    fun addJsInterface(webView: WebView, autoconsentCallback: AutoconsentCallback)
}

interface AutoconsentCallback {
    suspend fun onFirstPopUpHandled(dialogFragment: DialogFragment)
    fun onPopUpHandled()
}
