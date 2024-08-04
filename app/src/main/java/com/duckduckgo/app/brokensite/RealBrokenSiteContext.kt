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
import com.duckduckgo.browser.api.brokensite.BrokenSiteContext
import com.duckduckgo.browser.api.brokensite.BrokenSiteOpenerContext
import com.duckduckgo.common.utils.isHttp
import com.duckduckgo.common.utils.isHttps
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import org.json.JSONArray
import timber.log.Timber

@ContributesBinding(AppScope::class)
class RealBrokenSiteContext @Inject constructor(
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
) : BrokenSiteContext {
    init {
        Timber.d("KateTesting: New instance of RealBrokenSiteContext created")
    }

    override var userRefreshCount: Int = 0

    override var openerContext: BrokenSiteOpenerContext? = null

    override var jsPerformance: DoubleArray? = null

    override fun onUserTriggeredRefresh() {
        userRefreshCount++
        Timber.d("KateTesting: userRefreshCount increased to $userRefreshCount ")
    }

    override fun inferOpenerContext(
        referrer: String?,
        isExternalLaunch: Boolean,
    ) {
        if (isExternalLaunch) {
            openerContext = BrokenSiteOpenerContext.EXTERNAL
            Timber.d("KateTesting: OpenerContext assigned -> ${openerContext?.context}")
        } else if (referrer != null) {
            Timber.d("KateTesting: inferOpenerContext -> ref: $referrer")
            openerContext = when {
                duckDuckGoUrlDetector.isDuckDuckGoUrl(referrer) -> BrokenSiteOpenerContext.SERP
                referrer.toUri().isHttp || referrer.toUri().isHttps -> BrokenSiteOpenerContext.NAVIGATION
                else -> null
            }
            Timber.d(
                "KateTesting: OpenerContext assigned -> ${openerContext?.context}",
            )
        } else {
            Timber.d("KateTesting: OpenerContext not assigned bc referrer is null")
        }
    }

    override fun recordJsPerformance(performanceMetrics: JSONArray) {
        val recordedJsValues = DoubleArray(performanceMetrics.length())
        for (i in 0 until performanceMetrics.length()) {
            recordedJsValues[i] = performanceMetrics.getDouble(i)
        }
        jsPerformance = recordedJsValues
        Timber.d("KateTesting: jsPerformance recorded as $performanceMetrics")
    }
}
