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

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Per-instance settings of the Duck.ai widget, chosen in [DuckAiWidgetConfigurationActivity]. */
interface DuckAiWidgetPreferences {
    /** Defaults to true, also for widgets placed before the setting existed. */
    suspend fun showRecentChats(appWidgetId: Int): Boolean

    suspend fun setShowRecentChats(
        appWidgetId: Int,
        show: Boolean,
    )

    suspend fun remove(appWidgetIds: IntArray)
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class SharedPreferencesDuckAiWidgetPreferences @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val dispatcherProvider: DispatcherProvider,
) : DuckAiWidgetPreferences {

    private val preferences: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }

    override suspend fun showRecentChats(appWidgetId: Int): Boolean =
        withContext(dispatcherProvider.io()) { preferences.getBoolean(key(appWidgetId), true) }

    override suspend fun setShowRecentChats(
        appWidgetId: Int,
        show: Boolean,
    ) = withContext(dispatcherProvider.io()) {
        preferences.edit(commit = true) { putBoolean(key(appWidgetId), show) }
    }

    override suspend fun remove(appWidgetIds: IntArray) = withContext(dispatcherProvider.io()) {
        preferences.edit(commit = true) { appWidgetIds.forEach { remove(key(it)) } }
    }

    private fun key(appWidgetId: Int) = "$KEY_SHOW_RECENT_CHATS-$appWidgetId"

    private companion object {
        const val FILENAME = "com.duckduckgo.duckchat.impl.widget.DuckAiWidgetPreferences"
        const val KEY_SHOW_RECENT_CHATS = "KEY_SHOW_RECENT_CHATS"
    }
}
