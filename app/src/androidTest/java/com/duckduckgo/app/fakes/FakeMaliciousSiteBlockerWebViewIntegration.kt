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

package com.duckduckgo.app.fakes

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockerWebViewIntegration
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.MaliciousSite
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.Safe
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus

class FakeMaliciousSiteBlockerWebViewIntegration(
    private val isSafe: Boolean = true,
) : MaliciousSiteBlockerWebViewIntegration {
    override suspend fun shouldIntercept(
        request: WebResourceRequest,
        documentUri: Uri?,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousViewData {
        return if (isSafe) Safe(request.isForMainFrame) else MaliciousSite("foo.com".toUri(), Feed.MALWARE, false, true)
    }

    override fun shouldOverrideUrlLoading(
        url: Uri,
        isForMainFrame: Boolean,
        confirmationCallback: (maliciousStatus: MaliciousStatus) -> Unit,
    ): IsMaliciousViewData {
        return if (isSafe) Safe(isForMainFrame) else MaliciousSite("foo.com".toUri(), Feed.MALWARE, false, true)
    }

    override fun onPageLoadStarted(url: String) {
        // no-op
    }

    override fun onSiteExempted(
        url: Uri,
        feed: Feed,
    ) {
        TODO("Not yet implemented")
    }
}
