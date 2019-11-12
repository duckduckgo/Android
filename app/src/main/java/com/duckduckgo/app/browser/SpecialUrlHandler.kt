/*
 * Copyright (c) 2019 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser

import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.IntentType
import timber.log.Timber


interface SpecialUrlHandler {

    /**
     * We might need to apply special treatment to some URLs (for example, launching 3rd party apps)
     *
     * @return if the request should be overridden or not
     */
    fun handleUrl(webView: WebView, urlType: SpecialUrlDetector.UrlType, webViewClientListener: WebViewClientListener?): Boolean

    companion object {
        const val MAIN_FRAME_HANDLER = "MAIN_FRAME"
        const val SUB_FRAME_HANDLER = "SUB_FRAME"
    }
}

class MainFrameUrlHandler(private val requestRewriter: RequestRewriter) : SpecialUrlHandler {

    override fun handleUrl(webView: WebView, urlType: SpecialUrlDetector.UrlType, webViewClientListener: WebViewClientListener?): Boolean {

        return when (urlType) {
            is SpecialUrlDetector.UrlType.Email -> consume { webViewClientListener?.sendEmailRequested(urlType.emailAddress) }
            is SpecialUrlDetector.UrlType.Telephone -> consume { webViewClientListener?.dialTelephoneNumberRequested(urlType.telephoneNumber) }
            is SpecialUrlDetector.UrlType.Sms -> consume { webViewClientListener?.sendSmsRequested(urlType.telephoneNumber) }
            is IntentType -> consume {
                Timber.i("Found intent type link for $urlType.url")
                launchExternalApp(urlType, webViewClientListener)
            }
            is SpecialUrlDetector.UrlType.Unknown -> {
                Timber.w("Unable to process link type for ${urlType.url}")
                webView.loadUrl(webView.originalUrl)
                return false
            }
            is SpecialUrlDetector.UrlType.SearchQuery -> return false
            is SpecialUrlDetector.UrlType.Web -> {
                val uri = urlType.webAddress.toUri()
                if (requestRewriter.shouldRewriteRequest(uri)) {
                    val newUri = requestRewriter.rewriteRequestWithCustomQueryParams(uri)
                    webView.loadUrl(newUri.toString())
                    return true
                }
                return false
            }
        }
    }

    private fun launchExternalApp(urlType: IntentType, webViewClientListener: WebViewClientListener?) {
        webViewClientListener?.externalAppLinkClicked(urlType)
    }

    /**
     * Utility to function to execute a function, and then return true
     *
     * Useful to reduce clutter in repeatedly including `return true` after doing the real work.
     */
    private inline fun consume(function: () -> Unit): Boolean {
        function()
        return true
    }
}

/**
 * We need to ensure we don't launch external apps from URLs loading inside iFrames
 * If we detect we are not in the main frame, the only URL loading we support is web requests
 */
class SubFrameUrlHandler : SpecialUrlHandler {

    override fun handleUrl(webView: WebView, urlType: SpecialUrlDetector.UrlType, webViewClientListener: WebViewClientListener?): Boolean {

        return when (urlType) {
            is SpecialUrlDetector.UrlType.Web -> false
            else -> true
        }
    }

}