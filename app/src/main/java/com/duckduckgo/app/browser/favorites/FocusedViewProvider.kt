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

package com.duckduckgo.app.browser.favorites

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.FocusedViewPlugin
import com.duckduckgo.newtabpage.api.FocusedViewVersion
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface FocusedViewProvider {

    fun provideFocusedViewVersion(): Flow<FocusedViewPlugin>
}

@ContributesBinding(
    scope = ActivityScope::class,
)
class RealFocusedViewProvider @Inject constructor(
    private val focusedViewVersions: ActivePluginPoint<@JvmSuppressWildcards FocusedViewPlugin>,
) : FocusedViewProvider {
    override fun provideFocusedViewVersion(): Flow<FocusedViewPlugin> = flow {
        val focusedView = focusedViewVersions.getPlugins().firstOrNull() ?: FocusedLegacyPage()
        emit(focusedView)
    }
}

@ContributesActivePlugin(
    scope = ActivityScope::class,
    boundType = FocusedViewPlugin::class,
)
class FocusedLegacyPage @Inject constructor() : FocusedViewPlugin {

    override val name: String = FocusedViewVersion.LEGACY.name

    override fun getView(context: Context): View {
        return FocusedLegacyView(context)
    }
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = FocusedViewPlugin::class,
)
private interface FocusedViewPluginPointTrigger
