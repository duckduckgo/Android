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

package com.duckduckgo.app.browser.pdf

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface PdfDownloadTooltipDataStore {
    suspend fun incrementShownCount()
    suspend fun canShow(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SharedPreferencesPdfDownloadTooltipDataStore @Inject constructor(
    @PdfDownloadTooltip private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : PdfDownloadTooltipDataStore {

    private object Keys {
        val SHOWN_COUNT_KEY = intPreferencesKey(name = "PDF_DOWNLOAD_TOOLTIP_SHOWN_COUNT")
    }

    override suspend fun incrementShownCount() {
        withContext(dispatchers.io()) {
            dataStore.edit { prefs ->
                prefs[Keys.SHOWN_COUNT_KEY] = (prefs[Keys.SHOWN_COUNT_KEY] ?: 0) + 1
            }
        }
    }

    override suspend fun canShow(): Boolean = withContext(dispatchers.io()) {
        val count = dataStore.data.firstOrNull()?.let { it[Keys.SHOWN_COUNT_KEY] } ?: 0
        count < MAX_SHOWN_COUNT
    }

    private companion object {
        const val MAX_SHOWN_COUNT = 3
    }
}
