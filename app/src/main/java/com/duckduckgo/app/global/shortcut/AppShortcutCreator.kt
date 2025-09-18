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
import androidx.annotation.UiThread
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.ui.themepreview.ui.AppComponentsActivity
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksActivity
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat

@Module
@ContributesTo(AppScope::class)
class AppShortcutCreatorModule {
    @Provides
    @IntoSet
    fun provideAppShortcutCreatorObserver(
        appShortcutCreator: AppShortcutCreator,
    ): MainProcessLifecycleObserver {
        return AppShortcutCreatorLifecycleObserver(appShortcutCreator)
    }
}

class AppShortcutCreatorLifecycleObserver(
    private val appShortcutCreator: AppShortcutCreator,
) : MainProcessLifecycleObserver {
    @UiThread
    override fun onCreate(owner: LifecycleOwner) {
        logcat(INFO) { "Configure app shortcuts" }
        appShortcutCreator.refreshAppShortcuts()
    }
}

@SingleInstanceIn(AppScope::class)
class AppShortcutCreator @Inject constructor(
    private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val appBuildConfig: AppBuildConfig,
    private val duckChat: DuckChat,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val dispatchers: DispatcherProvider,
) {

    init {
        duckAiFeatureState.showPopupMenuShortcut
            .onEach { refreshAppShortcuts() }
            .flowOn(dispatchers.io())
            .launchIn(appCoroutineScope)
    }

    fun refreshAppShortcuts() {
        appCoroutineScope.launch(dispatchers.io()) {
            val shortcutList = mutableListOf<ShortcutInfo>()

            shortcutList.add(buildNewTabShortcut(context))
            shortcutList.add(buildClearDataShortcut(context))
            shortcutList.add(buildBookmarksShortcut(context))

            if (duckAiFeatureState.showPopupMenuShortcut.value) {
                shortcutList.add(buildDuckChatShortcut(context))
            } else if (appBuildConfig.isInternalBuild()) {
                shortcutList.add(buildAndroidDesignSystemShortcut(context))
            }

            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            kotlin.runCatching { shortcutManager.dynamicShortcuts = shortcutList }
        }
    }

    private fun buildNewTabShortcut(context: Context): ShortcutInfo {
        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_NEW_TAB)
            .setShortLabel(context.getString(R.string.newTabMenuItem))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_app_shortcut_new_tab))
            .setIntent(
                Intent(context, BrowserActivity::class.java).also {
                    it.action = Intent.ACTION_VIEW
                    it.putExtra(BrowserActivity.NEW_SEARCH_EXTRA, true)
                },
            )
            .build().toShortcutInfo()
    }

    private fun buildClearDataShortcut(context: Context): ShortcutInfo {
        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_CLEAR_DATA)
            .setShortLabel(context.getString(R.string.fireMenu))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_app_shortcut_fire))
            .setIntent(
                Intent(context, BrowserActivity::class.java).also {
                    it.action = Intent.ACTION_VIEW
                    it.putExtra(BrowserActivity.PERFORM_FIRE_ON_ENTRY_EXTRA, true)
                },
            )
            .build().toShortcutInfo()
    }

    private fun buildBookmarksShortcut(context: Context): ShortcutInfo {
        val browserActivity = BrowserActivity.intent(
            context = context,
            isLaunchFromBookmarksAppShortcut = true,
        ).also { it.action = Intent.ACTION_VIEW }
        val bookmarksActivity = BookmarksActivity.intent(context).also { it.action = Intent.ACTION_VIEW }

        val stackBuilder = TaskStackBuilder.create(context)
            .addNextIntent(browserActivity)
            .addNextIntent(bookmarksActivity)

        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_SHOW_BOOKMARKS)
            .setShortLabel(context.getString(com.duckduckgo.saved.sites.impl.R.string.bookmarksActivityTitle))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_app_shortcut_bookmarks))
            .setIntents(stackBuilder.intents)
            .build().toShortcutInfo()
    }

    private fun buildAndroidDesignSystemShortcut(context: Context): ShortcutInfo {
        val browserActivity = BrowserActivity.intent(context).also { it.action = Intent.ACTION_VIEW }
        val settingsActivity = SettingsActivity.intent(context).also { it.action = Intent.ACTION_VIEW }
        val adsActivity = AppComponentsActivity.intent(context).also { it.action = Intent.ACTION_VIEW }

        val stackBuilder = TaskStackBuilder.create(context)
            .addNextIntent(browserActivity)
            .addNextIntent(settingsActivity)
            .addNextIntent(adsActivity)

        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_DESIGN_SYSTEM_DEMO)
            .setShortLabel(context.getString(com.duckduckgo.mobile.android.R.string.ads_demo_activity_title))
            .setIcon(IconCompat.createWithResource(context, com.duckduckgo.mobile.android.R.drawable.ic_dax_icon))
            .setIntents(stackBuilder.intents)
            .build().toShortcutInfo()
    }

    private fun buildDuckChatShortcut(context: Context): ShortcutInfo {
        val browserActivity = BrowserActivity.intent(context, openDuckChat = true).also { it.action = Intent.ACTION_VIEW }
        val stackBuilder = TaskStackBuilder.create(context)
            .addNextIntent(browserActivity)

        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_DUCK_AI)
            .setShortLabel(context.getString(com.duckduckgo.duckchat.impl.R.string.duck_chat_title))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_app_shortcut_duck_ai))
            .setIntents(stackBuilder.intents)
            .build().toShortcutInfo()
    }

    companion object {
        private const val SHORTCUT_ID_CLEAR_DATA = "clearData"
        private const val SHORTCUT_ID_NEW_TAB = "newTab"
        private const val SHORTCUT_ID_SHOW_BOOKMARKS = "showBookmarks"
        private const val SHORTCUT_ID_DESIGN_SYSTEM_DEMO = "designSystemDemo"
        private const val SHORTCUT_ID_DUCK_AI = "duckAI"
    }
}
