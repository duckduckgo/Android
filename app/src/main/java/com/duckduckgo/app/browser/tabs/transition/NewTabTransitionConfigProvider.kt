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

package com.duckduckgo.app.browser.tabs.transition

import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NewTabTransitionConfigProvider {
    /** Null when transitions are off. */
    suspend fun enabledConfig(): NewTabTransitionConfig?
}

@ContributesBinding(AppScope::class)
class RealNewTabTransitionConfigProvider @Inject constructor(
    private val tabManagerFeatureFlags: TabManagerFeatureFlags,
    private val dispatchers: DispatcherProvider,
) : NewTabTransitionConfigProvider {

    override suspend fun enabledConfig(): NewTabTransitionConfig? {
        val enabled = withContext(dispatchers.io()) { tabManagerFeatureFlags.newTabTransitions().isEnabled() }
        return if (enabled) NewTabTransitionConfig.DEFAULT else null
    }
}
