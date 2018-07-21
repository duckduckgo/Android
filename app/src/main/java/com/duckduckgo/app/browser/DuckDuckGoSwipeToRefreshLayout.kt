package com.duckduckgo.app.browser

import android.content.Context
import android.support.v4.widget.SwipeRefreshLayout
import android.util.AttributeSet
import android.webkit.WebView
import android.webkit.WebViewClient

class DuckDuckGoSwipeToRefreshLayout @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null) :
    SwipeRefreshLayout(context, attributeSet) {

    override fun onFinishInflate() {
        super.onFinishInflate()
        val webView = getChildAt(1)
        if (webView !is WebView) {
            throw UnsupportedOperationException("Must use with a webview")
        }
        setOnRefreshListener {
            webView.reload()
        }
    }

    fun wrapWebViewClient(webViewClient: WebViewClient) = WebViewClientDecorator(webViewClient)

    inner class WebViewClientDecorator(private val webClient: WebViewClient?) : WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            webClient?.onPageFinished(view, url)
            isRefreshing = false
        }
    }
}