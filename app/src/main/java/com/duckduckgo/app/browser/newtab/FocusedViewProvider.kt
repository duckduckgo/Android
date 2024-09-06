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

package com.duckduckgo.app.browser.newtab

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.FocusedViewPlugin
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface FocusedViewProvider {

    fun provideFocusedViewVersion(): Flow<FocusedViewPlugin>
}

@ContributesBinding(
    scope = AppScope::class,
)
class RealFocusedViewProvider @Inject constructor(
    private val focusedViewVersions: ActivePluginPoint<FocusedViewPlugin>,
) : FocusedViewProvider {
    override fun provideFocusedViewVersion(): Flow<FocusedViewPlugin> = flow {
        val focusedView = focusedViewVersions.getPlugins().firstOrNull() ?: FocusedLegacyPage()
        emit(focusedView)
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = FocusedViewPlugin::class,
    priority = FocusedViewPlugin.PRIORITY_LEGACY_FOCUSED_PAGE,
    supportExperiments = true,
)
class FocusedLegacyPage @Inject constructor() : FocusedViewPlugin {

    override fun getView(context: Context): View {
        return FocusedLegacyView(context)
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = FocusedViewPlugin::class,
    priority = FocusedViewPlugin.PRIORITY_NEW_FOCUSED_PAGE,
    defaultActiveValue = false,
    supportExperiments = true,
    internalAlwaysEnabled = true,
)
class FocusedPage @Inject constructor() : FocusedViewPlugin {

    override fun getView(context: Context): View {
        return FocusedView(context)
    }
}
