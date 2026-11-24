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

package com.duckduckgo.pagetranslation.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface TranslationPreferencesStore {
    /**
     * The "Ask to Translate Pages" setting, on by default. It only controls the banner, the menu row stays.
     */
    val askToTranslatePages: Flow<Boolean>

    suspend fun setAskToTranslatePages(enabled: Boolean)
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealTranslationPreferencesStore @Inject constructor(
    @TranslationPreferences private val store: DataStore<Preferences>,
) : TranslationPreferencesStore {

    override val askToTranslatePages: Flow<Boolean> = store.data.map { it[KEY_ASK_TO_TRANSLATE_PAGES] ?: true }

    override suspend fun setAskToTranslatePages(enabled: Boolean) {
        store.edit { it[KEY_ASK_TO_TRANSLATE_PAGES] = enabled }
    }

    private companion object {
        val KEY_ASK_TO_TRANSLATE_PAGES = booleanPreferencesKey("ASK_TO_TRANSLATE_PAGES")
    }
}
