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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.duckchat.impl.di.DuckChat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.IOException
import javax.inject.Inject

class UsageNoticeDismissalStore @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
) {
    /** One dismissal per window, so closing the daily card does not forget the weekly one. */
    val dismissals: Flow<Map<UsageWindow, UsageNoticeDismissal>> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            UsageWindow.entries.mapNotNull { window -> preferences.dismissal(window)?.let { window to it } }.toMap()
        }
        .distinctUntilChanged()

    suspend fun dismiss(notice: UsageNotice) {
        val keys = Keys(notice.window)
        editIgnoringIoFailure { preferences ->
            preferences[keys.noticeId] = notice.id.jsonId
            preferences[keys.resetsAt] = notice.resetsAtMillis
            preferences[keys.band] = UsageNoticeBand.of(notice.percentUsed)
        }
    }

    suspend fun clear() {
        editIgnoringIoFailure { preferences ->
            UsageWindow.entries.forEach { window ->
                val keys = Keys(window)
                preferences.remove(keys.noticeId)
                preferences.remove(keys.resetsAt)
                preferences.remove(keys.band)
            }
        }
    }

    private fun Preferences.dismissal(window: UsageWindow): UsageNoticeDismissal? {
        val keys = Keys(window)
        val noticeId = UsageNoticeId.fromJsonId(this[keys.noticeId]) ?: return null
        val resetsAt = this[keys.resetsAt] ?: return null
        val band = this[keys.band] ?: return null
        return UsageNoticeDismissal(noticeId = noticeId, window = window, resetsAtMillis = resetsAt, band = band)
    }

    // A failed write must not take the app down for a footer dismissal; the read side already tolerates IO errors.
    private suspend fun editIgnoringIoFailure(transform: (MutablePreferences) -> Unit) {
        try {
            store.edit(transform)
        } catch (e: IOException) {
            logcat(WARN) { "Duck.ai usage warnings: dismissal write failed: ${e.message}" }
        }
    }

    private class Keys(window: UsageWindow) {
        private val prefix = "DUCK_AI_USAGE_NOTICE_DISMISSED_${window.name}"
        val noticeId = stringPreferencesKey("${prefix}_ID")
        val resetsAt = longPreferencesKey("${prefix}_RESETS_AT")
        val band = intPreferencesKey("${prefix}_BAND")
    }
}
