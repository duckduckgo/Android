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
import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.FocusedViewPlugin
import com.duckduckgo.newtabpage.api.FocusedViewVersion
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

interface FocusedViewProvider {

    fun provideFocusedViewVersion(): FocusedViewPlugin
}

@ContributesBinding(
    scope = ActivityScope::class,
)
class RealFocusedViewProvider @Inject constructor(
    private val focusedViewVersions: PluginPoint<FocusedViewPlugin>,
) : FocusedViewProvider {
    override fun provideFocusedViewVersion(): FocusedViewPlugin {
        val focusedView = focusedViewVersions.getPlugins().firstOrNull { it.name == FocusedViewVersion.LEGACY.name }
        return focusedView ?: FocusedLegacyPage()
    }
}

@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = FocusedViewPlugin::class,
)
class FocusedLegacyPage @Inject constructor() : FocusedViewPlugin {

    override val name: String = FocusedViewVersion.LEGACY.name

    override fun getView(context: Context): View {
        return FocusedLegacyView(context)
    }
}

@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = FocusedViewPlugin::class,
)
private interface FocusedViewPluginPointTrigger
