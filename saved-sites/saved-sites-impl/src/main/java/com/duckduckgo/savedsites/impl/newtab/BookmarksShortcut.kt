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

package com.duckduckgo.savedsites.impl.newtab

import android.content.Context
import android.content.Intent
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.browser.api.ui.BrowserScreens.BookmarksScreenNoParams
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import com.duckduckgo.saved.sites.impl.R
import javax.inject.Inject

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageShortcutPlugin::class,
    priority = NewTabPageShortcutPlugin.PRIORITY_BOOKMARKS,
)
class BookmarksNewTabShortcutPlugin @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val setting: BookmarksNewTabShortcutSetting,
) : NewTabPageShortcutPlugin {

    inner class BookmarksShortcut() : NewTabShortcut {
        override fun name(): String = "bookmarks"
        override fun titleResource(): Int = R.string.newTabPageShortcutBookmarks
        override fun iconResource(): Int = R.drawable.ic_shortcut_bookmarks
    }

    override fun getShortcut(): NewTabShortcut {
        return BookmarksShortcut()
    }

    override fun onClick(context: Context) {
        val intent = globalActivityStarter.startIntent(context, BookmarksScreenNoParams)?.apply {
            action = Intent.ACTION_VIEW
        }

        intent?.let { context.startActivity(intent) }
    }

    override suspend fun isUserEnabled(): Boolean {
        return setting.self().isEnabled()
    }

    override suspend fun setUserEnabled(enabled: Boolean) {
        if (enabled) {
            setting.self().setRawStoredState(Toggle.State(true))
        } else {
            setting.self().setRawStoredState(Toggle.State(false))
        }
    }
}

/**
 * Local feature/settings - they will never be in remote config
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "bookmarksNewTabShortcutSetting",
)
interface BookmarksNewTabShortcutSetting {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
