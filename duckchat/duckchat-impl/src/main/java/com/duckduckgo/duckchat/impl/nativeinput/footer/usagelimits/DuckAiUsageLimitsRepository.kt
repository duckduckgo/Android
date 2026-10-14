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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject

interface DuckAiUsageLimitsRepository {
    /**
     * The current usage snapshot for [browserMode], or null when there is nothing to show.
     */
    fun usageLimits(browserMode: BrowserMode): Flow<UsageLimitsSnapshot?>
}

@ContributesBinding(AppScope::class)
class RealDuckAiUsageLimitsRepository @Inject constructor(
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage>,
    private val parser: UsageLimitsSnapshotParser,
    private val dispatchers: DispatcherProvider,
) : DuckAiUsageLimitsRepository {

    override fun usageLimits(browserMode: BrowserMode): Flow<UsageLimitsSnapshot?> {
        // Fire mode always emits null without reading storage.
        if (browserMode == BrowserMode.FIRE) return flowOf(null)
        return storageProvider.forMode(browserMode).settings
            .observe(USAGE_LIMITS_KEY)
            .map { entity -> parser.parse(entity?.value) }
            .distinctUntilChanged()
            .onEach { snapshot -> logcat { "Duck.ai usage limits: ${snapshot.describe()}" } }
            .flowOn(dispatchers.io())
    }

    private fun UsageLimitsSnapshot?.describe(): String {
        if (this == null) return "no notice"
        return "notice=${notice.id.jsonId} window=${notice.window.jsonId} percent=${notice.percentUsed} " +
            "reached=${notice.reached} cta=${cta?.id?.jsonId ?: "none"}"
    }

    companion object {
        const val USAGE_LIMITS_KEY = "usageLimits"
    }
}
