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

package com.duckduckgo.brokensite.impl

import androidx.core.net.toUri
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.brokensite.api.BrokenSiteApiOpenerContext
import com.duckduckgo.brokensite.api.BrokenSiteContext
import com.duckduckgo.common.utils.isHttp
import com.duckduckgo.common.utils.isHttps
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrokenSiteContext @Inject constructor(
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
) : BrokenSiteContext {

    override var userRefreshCount: Int = 0

    override var isLaunchedFromExternalApp: Boolean = false

    override var openerContext: BrokenSiteApiOpenerContext? = null

    override var jsPerformance: DoubleArray? = null

    override fun onUserTriggeredRefresh() {
        userRefreshCount++
        Timber.d("KateTesting: userRefreshCount increased to $userRefreshCount ")
    }

    override fun inferOpenerContext(
        referrer: String?
    ) {
        if (referrer != null && !isLaunchedFromExternalApp) {
            Timber.d("KateTesting: referrer is NOT null -> $referrer")
            openerContext = when {
                duckDuckGoUrlDetector.isDuckDuckGoUrl(referrer) -> BrokenSiteApiOpenerContext.SERP
                referrer.toUri().isHttp || referrer.toUri().isHttps -> BrokenSiteApiOpenerContext.NAVIGATION
                else -> null
            }
            Timber.d("KateTesting: OpenerContext assigned -> ${openerContext?.context} from referrer: $referrer")
        } else {
            Timber.d("KateTesting: OpenerContext not assigned bc either referrer=='' -> " +
                "($referrer) or isLaunchedFromExternalApp -> ($isLaunchedFromExternalApp)")
        }
    }

    override fun setExternalOpenerContext() {
        isLaunchedFromExternalApp = true
        openerContext = BrokenSiteApiOpenerContext.EXTERNAL
    }

    override fun recordJsPerformance(jsPerfMetrics: DoubleArray?) {
        if (jsPerfMetrics != null) {
            jsPerformance = jsPerfMetrics
        }
    }
}
