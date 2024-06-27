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

package com.duckduckgo.newtabpage.api

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.common.utils.plugins.ActivePlugin

/**
 * This class is used to provide each of the Shortcuts that build the Shortcut section in New Tab Page
 * Implementation of https://app.asana.com/0/1174433894299346/12070643725750
 */
interface NewTabPageShortcutPlugin : ActivePlugin {

    /**
     * This method returns a [NewTabShortcut] that will be used as the Shortcuts content
     * @return [NewTabShortcut]
     */
    fun getShortcut(): NewTabShortcut

    fun onClick(context: Context)

    /**
     * This method returns a [Boolean] that shows if the plugin is enabled manually by the user
     * @return [Boolean]
     */
    suspend fun isUserEnabled(): Boolean

    /**
     * Toggle shortcut visibility
     * Used from the New Tab Settings screen
     */
    suspend fun toggle()
}

enum class NewTabShortcut(val type: String, @StringRes val titleResource: Int, @DrawableRes val iconResource: Int) {
    Bookmarks("bookmarks", R.string.newTabPageShortcutBookmarks, R.drawable.ic_shortcut_bookmarks),
    Chat("chat", R.string.newTabPageShortcutChat, R.drawable.ic_shortcuts_ai_chat),
    Passwords("passwords", R.string.newTabPageShortcutPasswords, R.drawable.ic_shortcut_passwords),
    Downloads("downloads", R.string.newTabPageShortcutDownloads, R.drawable.ic_shortcut_downloads),
    Settings("settings", R.string.newTabPageShortcutSettings, R.drawable.ic_shortcut_settings),
}
