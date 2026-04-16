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

package com.duckduckgo.app.browser.menu

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealBrowserMenuHighlight @Inject constructor(
    private val plugins: PluginPoint<BrowserMenuHighlightPlugin>,
) : BrowserMenuHighlight {
    override fun shouldShowHighlightForMode(mode: BrowserViewMode): Flow<Boolean> {
        val pluginList = plugins.getPlugins()
        if (pluginList.isEmpty()) return flowOf(false)

        val applicable = pluginList.filter { mode in it.compatibleModes }
        if (applicable.isEmpty()) return flowOf(false)

        return combine(applicable.map { it.isHighlighted() }) { highlights ->
            highlights.any { it }
        }
    }
}
