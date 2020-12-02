/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.shortcut

import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import javax.inject.Inject

class AppShortcutCreator @Inject constructor() {

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun configureAppShortcuts(context: Context) {
        val shortcutList = mutableListOf<ShortcutInfo>()

        shortcutList.add(buildNewTabShortcut(context))
        shortcutList.add(buildClearDataShortcut(context))
        shortcutList.add(buildBookmarksShortcut(context))

        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        shortcutManager.dynamicShortcuts = shortcutList
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun buildNewTabShortcut(context: Context): ShortcutInfo {
        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_NEW_TAB)
            .setShortLabel(context.getString(R.string.newTabMenuItem))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_app_shortcut_new_tab))
            .setIntent(
                Intent(context, BrowserActivity::class.java).also {
                    it.action = Intent.ACTION_VIEW
                    it.putExtra(BrowserActivity.NEW_SEARCH_EXTRA, true)
                }
            )
            .build().toShortcutInfo()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun buildClearDataShortcut(context: Context): ShortcutInfo {
        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_CLEAR_DATA)
            .setShortLabel(context.getString(R.string.fireMenu))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_app_shortcut_fire))
            .setIntent(
                Intent(context, BrowserActivity::class.java).also {
                    it.action = Intent.ACTION_VIEW
                    it.putExtra(BrowserActivity.PERFORM_FIRE_ON_ENTRY_EXTRA, true)
                }
            )
            .build().toShortcutInfo()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun buildBookmarksShortcut(context: Context): ShortcutInfo {
        val bookmarksActivity = BookmarksActivity.intent(context).also { it.action = Intent.ACTION_VIEW }

        val stackBuilder = TaskStackBuilder.create(context).addNextIntentWithParentStack(bookmarksActivity)

        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_SHOW_BOOKMARKS)
            .setShortLabel(context.getString(R.string.bookmarksActivityTitle))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_app_shortcut_bookmarks))
            .setIntents(stackBuilder.intents)
            .build().toShortcutInfo()
    }

    companion object {
        private const val SHORTCUT_ID_CLEAR_DATA = "clearData"
        private const val SHORTCUT_ID_NEW_TAB = "newTab"
        private const val SHORTCUT_ID_SHOW_BOOKMARKS = "showBookmarks"
    }

}
