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
import java.io.IOException
import javax.inject.Inject

class UsageNoticeDismissalStore @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
) {
    val dismissal: Flow<UsageNoticeDismissal?> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            val noticeId = UsageNoticeId.fromJsonId(preferences[NOTICE_ID]) ?: return@map null
            val window = UsageWindow.fromJsonId(preferences[WINDOW]) ?: return@map null
            val resetsAt = preferences[RESETS_AT] ?: return@map null
            val band = preferences[BAND] ?: return@map null
            UsageNoticeDismissal(noticeId = noticeId, window = window, resetsAtMillis = resetsAt, band = band)
        }
        .distinctUntilChanged()

    suspend fun dismiss(notice: UsageNotice) {
        store.edit { preferences ->
            preferences[NOTICE_ID] = notice.id.jsonId
            preferences[WINDOW] = notice.window.jsonId
            preferences[RESETS_AT] = notice.resetsAtMillis
            preferences[BAND] = UsageNoticeBand.of(notice.percentUsed)
        }
    }

    suspend fun clear() {
        store.edit { preferences ->
            preferences.remove(NOTICE_ID)
            preferences.remove(WINDOW)
            preferences.remove(RESETS_AT)
            preferences.remove(BAND)
        }
    }

    private companion object {
        val NOTICE_ID = stringPreferencesKey("DUCK_AI_USAGE_NOTICE_DISMISSED_ID")
        val WINDOW = stringPreferencesKey("DUCK_AI_USAGE_NOTICE_DISMISSED_WINDOW")
        val RESETS_AT = longPreferencesKey("DUCK_AI_USAGE_NOTICE_DISMISSED_RESETS_AT")
        val BAND = intPreferencesKey("DUCK_AI_USAGE_NOTICE_DISMISSED_BAND")
    }
}
