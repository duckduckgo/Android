/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.settings.impl.serpsettings.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.settings.impl.serpsettings.di.SerpSettings
import com.duckduckgo.settings.impl.serpsettings.pixel.SerpSettingsPixelName
import com.duckduckgo.settings.impl.serpsettings.pixel.fireSerpSettingsCountAndDaily
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

interface SerpSettingsDataStore {

    suspend fun setSerpSettings(value: String)

    suspend fun getSerpSettings(): String?

    fun observeSerpSettings(): Flow<String?>

    /**
     * Atomically reads the current value, applies [transform], and stores the result within a single
     * DataStore transaction. Use this instead of a separate get/set pair when merging into the existing
     * blob, so a concurrent writer (e.g. the SERP's updateNativeSettings) can't cause a lost update.
     */
    suspend fun updateSerpSettings(transform: (String?) -> String)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SerpSettingsPrefsDataStore @Inject constructor(
    @SerpSettings private val store: DataStore<Preferences>,
    private val pixel: Pixel,
) : SerpSettingsDataStore {

    // observeSerpSettings() fans out to many collectors (each SERP key the app observes installs its own
    // collection of store.data), so a single DataStore read failure would otherwise fire one read-error pixel
    // per active collector. This latch reports only the first failure of an error episode and re-arms once a
    // healthy read succeeds, so one failure burst maps to one pixel.
    private val readErrorReported = AtomicBoolean(false)

    override suspend fun setSerpSettings(value: String) {
        runCatching { store.edit { prefs -> prefs[SERP_SETTINGS] = value } }
            .onFailure { fireWriteError() }
    }

    override suspend fun getSerpSettings(): String? =
        runCatching { store.data.firstOrNull()?.let { it[SERP_SETTINGS] } }
            .onSuccess { readErrorReported.set(false) }
            .getOrElse {
                fireReadError()
                null
            }

    override fun observeSerpSettings(): Flow<String?> =
        store.data
            .map { prefs -> prefs[SERP_SETTINGS] }
            .onEach { readErrorReported.set(false) }
            .catch {
                fireReadError()
                emit(null)
            }
            .distinctUntilChanged()

    override suspend fun updateSerpSettings(transform: (String?) -> String) {
        runCatching { store.edit { prefs -> prefs[SERP_SETTINGS] = transform(prefs[SERP_SETTINGS]) } }
            .onFailure { fireWriteError() }
    }

    // Fires at most once per read-error episode; re-armed by the next healthy read.
    private fun fireReadError() {
        if (readErrorReported.compareAndSet(false, true)) {
            pixel.fireSerpSettingsCountAndDaily(
                countPixel = SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_READ_ERROR_COUNT,
                dailyPixel = SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_READ_ERROR_DAILY,
            )
        }
    }

    private fun fireWriteError() = pixel.fireSerpSettingsCountAndDaily(
        countPixel = SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_WRITE_ERROR_COUNT,
        dailyPixel = SerpSettingsPixelName.SERP_SETTINGS_KEYVALUE_STORE_WRITE_ERROR_DAILY,
    )

    private companion object {
        private val SERP_SETTINGS = stringPreferencesKey(name = "SERP_SETTINGS")
    }
}
