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

package com.duckduckgo.pagetranslation.impl

import android.content.SharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

interface TranslationPreferencesStore {
    /**
     * The "Ask to Translate Pages" setting. It only controls the banner, the menu row stays.
     */
    var askToTranslatePages: Boolean
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealTranslationPreferencesStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : TranslationPreferencesStore {

    private val preferences: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }

    override var askToTranslatePages: Boolean
        get() = preferences.getBoolean(KEY_ASK_TO_TRANSLATE_PAGES, true)
        set(value) = preferences.edit().putBoolean(KEY_ASK_TO_TRANSLATE_PAGES, value).apply()

    private companion object {
        const val FILENAME = "com.duckduckgo.pagetranslation.store"
        const val KEY_ASK_TO_TRANSLATE_PAGES = "ASK_TO_TRANSLATE_PAGES"
    }
}
