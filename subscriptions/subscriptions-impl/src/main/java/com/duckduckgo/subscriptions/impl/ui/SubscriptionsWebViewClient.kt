/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.duckduckgo.app.browser.SpecialUrlDetector
import com.duckduckgo.subscriptions.impl.wideevents.SubscriptionRestoreWideEvent

class SubscriptionsWebViewClient(
    private val specialUrlDetector: SpecialUrlDetector,
    private val context: Context,
    private val onRenderProcessCrash: () -> Boolean,
    private val subscriptionRestoreWideEvent: SubscriptionRestoreWideEvent,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url
        return shouldOverride(view, url)
    }

    private fun shouldOverride(
        webView: WebView,
        url: Uri,
    ): Boolean {
        return try {
            when (val urlType = specialUrlDetector.determineType(initiatingUrl = webView.originalUrl, uri = url)) {
                is SpecialUrlDetector.UrlType.Telephone -> {
                    val intent = Intent(Intent.ACTION_DIAL)
                    intent.data = Uri.parse("tel:${urlType.telephoneNumber}")
                    context.startActivity(intent)
                    true
                }
                else -> false
            }
        } catch (e: Throwable) {
            false
        }
    }

    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean = onRenderProcessCrash()

    override fun doUpdateVisitedHistory(
        view: WebView?,
        url: String?,
        isReload: Boolean,
    ) {
        if (url != null) {
            subscriptionRestoreWideEvent.onSubscriptionWebViewUrlChanged(url)
        }
    }
}
