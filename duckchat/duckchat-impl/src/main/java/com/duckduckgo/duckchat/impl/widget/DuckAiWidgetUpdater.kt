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
package com.duckduckgo.duckchat.impl.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the Duck.ai widget in sync with what it renders: it follows the regular-mode chat store while an instance is
 * placed (collection stops itself once the last instance is gone) and is re-rendered on app start and when the remote
 * config changes, since flags can enable or disable Duck.ai or change how it is coloured.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<MainProcessLifecycleObserver>(),
)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<PrivacyConfigCallbackPlugin>(),
)
class DuckAiWidgetUpdater @Inject constructor(
    private val context: Context,
    private val chats: DuckAiChatsWidgetChats,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

    private var observeJob = ConflatedJob()

    // Feature flags, including local overrides, are read at render time, so a new process re-renders to pick them up.
    override fun onCreate(owner: LifecycleOwner) {
        refresh()
    }

    override fun onStart(owner: LifecycleOwner) {
        startObservingIfNeeded()
    }

    override fun onPrivacyConfigDownloaded() {
        refresh()
    }

    fun startObservingIfNeeded() {
        if (observeJob.isActive || widgetIds().isEmpty()) return

        observeJob += appCoroutineScope.launch {
            // The first emission is the state the widget has just rendered.
            chats.observeChats().drop(1).collect {
                if (widgetIds().isEmpty()) {
                    cancel()
                } else {
                    refresh()
                }
            }
        }
    }

    private fun widgetIds(): IntArray {
        val manager = AppWidgetManager.getInstance(context) ?: return IntArray(0)
        return manager.getAppWidgetIds(ComponentName(context, DuckAiWidgetProvider::class.java)) ?: IntArray(0)
    }

    private fun refresh() {
        val ids = widgetIds().takeIf { it.isNotEmpty() } ?: return
        val intent = Intent(context, DuckAiWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}
