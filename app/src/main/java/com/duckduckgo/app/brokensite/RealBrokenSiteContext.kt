/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.brokensite

import androidx.core.net.toUri
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.browser.api.brokensite.BrokenSiteOpenerContext
import com.duckduckgo.browser.api.brokensite.BrokenSiteContext
import com.duckduckgo.common.utils.isHttp
import com.duckduckgo.common.utils.isHttps
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealBrokenSiteContext @Inject constructor(
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
) : BrokenSiteContext {
    init {
        Timber.d("KateTesting: New instance of RealBrokenSiteContext created")
    }

    override var userRefreshCount: Int = 0

    override var isLaunchedFromExternalApp: Boolean = false

    override var openerContext: BrokenSiteOpenerContext? = null

    override var jsPerformance: List<Double>? = null

    override fun onUserTriggeredRefresh() {
        userRefreshCount++
        Timber.d("KateTesting: userRefreshCount increased to $userRefreshCount ")
    }

    override fun inferOpenerContext(
        referrer: String?,
        wasLaunchedExternally: Boolean?
    ) {
        if (referrer != null && wasLaunchedExternally != null) {
            Timber.d("KateTesting: inferOpenerContext -> ref: $referrer, external: $wasLaunchedExternally")
            openerContext = when {
                wasLaunchedExternally -> BrokenSiteOpenerContext.EXTERNAL
                duckDuckGoUrlDetector.isDuckDuckGoUrl(referrer) -> BrokenSiteOpenerContext.SERP
                referrer.toUri().isHttp || referrer.toUri().isHttps -> BrokenSiteOpenerContext.NAVIGATION
                else -> null
            }
            Timber.d("KateTesting: OpenerContext assigned -> ${openerContext?.context} from referrer: $referrer" +
                " or wasLaunchedExternally==$wasLaunchedExternally")
        } else {
            Timber.d("KateTesting: OpenerContext not assigned bc either referrer is null (${referrer==null}) " +
                "or wasLaunchedExternally is null (${wasLaunchedExternally==null})")
        }
    }

    override fun setExternalOpenerContext() {
        isLaunchedFromExternalApp = true
        println("KateTesting: isLaunchedExternally set to true")
        openerContext = BrokenSiteOpenerContext.EXTERNAL
        println("KateTesting: OpenerContext set to External")
    }

    override fun recordJsPerformance(jsPerfMetrics: MutableList<Double>?) {
            jsPerformance = jsPerfMetrics?.toList()
    }
}
