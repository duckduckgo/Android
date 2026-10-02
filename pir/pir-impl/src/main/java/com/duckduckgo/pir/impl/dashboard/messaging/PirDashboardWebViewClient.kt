/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.dashboard.messaging

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.duckduckgo.pir.impl.dashboard.purchase.PirFreemiumPurchaseUrlRouter
import com.duckduckgo.pir.impl.dashboard.purchase.PirPurchaseRoute.NativePurchaseFlow
import com.duckduckgo.pir.impl.dashboard.purchase.PirPurchaseRoute.NotHandled
import javax.inject.Inject

/**
 * Custom implementation of [WebViewClient] specific to PIR
 */
class PirDashboardWebViewClient @Inject constructor(
    private val purchaseUrlRouter: PirFreemiumPurchaseUrlRouter,
) : WebViewClient() {

    interface Listener {
        fun onSubscriptionPurchaseRequested(route: NativePurchaseFlow)
    }

    /** Set by the hosting Activity in setupWebView and cleared in cleanupWebView. */
    var listener: Listener? = null

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (!request.isForMainFrame) return false

        return when (val route = purchaseUrlRouter.route(request.url)) {
            is NativePurchaseFlow -> {
                listener?.onSubscriptionPurchaseRequested(route)
                true
            }
            NotHandled -> false
        }
    }
}
