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
import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPagePlugin
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface NewTabPageProvider {

    fun provideNewTabPageVersion(): Flow<NewTabPagePlugin>
}

@ContributesBinding(scope = ActivityScope::class)
class RealNewTabPageProvider @Inject constructor(
    private val newTabPageVersions: ActivePluginPoint<NewTabPagePlugin>,
) : NewTabPageProvider {
    override fun provideNewTabPageVersion(): Flow<NewTabPagePlugin> = flow {
        val newTabPage = newTabPageVersions.getPlugins().firstOrNull() ?: NewTabLegacyPage()
        emit(newTabPage)
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NewTabPagePlugin::class,
    priority = NewTabPagePlugin.PRIORITY_NTP,
)
class NewTabLegacyPage @Inject constructor() : NewTabPagePlugin {

    override fun getView(
        context: Context,
        showLogo: Boolean,
        onHasContent: ((Boolean) -> Unit)?,
    ): View {
        return NewTabLegacyPageView(context, showLogo = showLogo, onHasContent = onHasContent)
    }
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NewTabPagePlugin::class,
)
private interface NewTabPagePluginPointTrigger
