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
import com.duckduckgo.app.newtab.NewTabPagePlugin
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

interface NewTabPageProvider {

    fun provideNewTabPageVersion(): NewTabPagePlugin
}

@ContributesBinding(
    scope = ActivityScope::class,
)
class RealNewTabPageProvider @Inject constructor(
    private val newTabPageVersions: PluginPoint<NewTabPagePlugin>,
) : NewTabPageProvider {
    override fun provideNewTabPageVersion(): NewTabPagePlugin {
        val newTabPage = newTabPageVersions.getPlugins().firstOrNull { it.name == NewTabPageVersion.LEGACY.name }
        return newTabPage ?: NewTabLegacyPage()
    }
}

@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = NewTabPagePlugin::class,
)
class NewTabLegacyPage @Inject constructor() : NewTabPagePlugin {

    override val name: String = NewTabPageVersion.LEGACY.name

    override fun getView(context: Context): View {
        return NewTabLegacyPageView(context)
    }
}

@ContributesMultibinding(
    scope = ActivityScope::class,
    boundType = NewTabPagePlugin::class,
)
class NewTabPage @Inject constructor() : NewTabPagePlugin {

    override val name: String = NewTabPageVersion.NEW.name
    override fun getView(context: Context): View {
        return NewTabLegacyPageView(context)
    }
}

internal enum class NewTabPageVersion {
    LEGACY,
    NEW,
}

@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = NewTabPagePlugin::class,
)
private interface NewTabPagePluginPointTrigger
