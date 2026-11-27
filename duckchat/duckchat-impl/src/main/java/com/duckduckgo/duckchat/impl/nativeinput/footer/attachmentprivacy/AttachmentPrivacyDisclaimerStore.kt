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

package com.duckduckgo.duckchat.impl.nativeinput.footer.attachmentprivacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.duckduckgo.duckchat.impl.di.DuckChat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.IOException
import javax.inject.Inject

class AttachmentPrivacyDisclaimerStore @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
) {
    val shown: Flow<Boolean> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[DUCK_AI_ATTACHMENT_PRIVACY_DISCLAIMER_SHOWN] ?: false }
        .distinctUntilChanged()

    suspend fun markShown() {
        try {
            store.edit { preferences -> preferences[DUCK_AI_ATTACHMENT_PRIVACY_DISCLAIMER_SHOWN] = true }
        } catch (e: IOException) {
            logcat(WARN) { "Duck.ai attachment privacy disclaimer: write failed: ${e.message}" }
        }
    }

    private companion object {
        val DUCK_AI_ATTACHMENT_PRIVACY_DISCLAIMER_SHOWN = booleanPreferencesKey("DUCK_AI_ATTACHMENT_PRIVACY_DISCLAIMER_SHOWN")
    }
}
