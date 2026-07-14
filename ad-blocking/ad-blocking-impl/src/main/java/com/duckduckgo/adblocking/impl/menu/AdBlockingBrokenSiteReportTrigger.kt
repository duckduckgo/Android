/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.menu

import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.browser.api.brokensite.BrokenSiteReportTriggerPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

interface BrokenSiteReportRequester {
    fun requestReport()
}

@ContributesBinding(AppScope::class, boundType = BrokenSiteReportRequester::class)
@ContributesMultibinding(AppScope::class, boundType = BrokenSiteReportTriggerPlugin::class)
@SingleInstanceIn(AppScope::class)
class AdBlockingBrokenSiteReportTrigger @Inject constructor() :
    BrokenSiteReportTriggerPlugin,
    BrokenSiteReportRequester {

    private val reportRequests = MutableSharedFlow<BrokenSiteData.ReportFlow>(extraBufferCapacity = 1)

    override fun observeReportRequests(): Flow<BrokenSiteData.ReportFlow> = reportRequests

    override fun requestReport() {
        reportRequests.tryEmit(BrokenSiteData.ReportFlow.MENU)
    }
}
