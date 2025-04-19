/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.firebutton

import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface FireButtonStore {
    val fireButttonUseCount: Int

    fun incrementFireButtonUseCount()
}

@ContributesBinding(AppScope::class)
class RealFireButtonStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : FireButtonStore {

    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    override val fireButttonUseCount: Int
        get() = preferences.getInt(KEY_FIREBUTTON_USE_COUNT, 0)

    override fun incrementFireButtonUseCount() {
        val currentCount = fireButttonUseCount
        preferences.edit {
            putInt(KEY_FIREBUTTON_USE_COUNT, currentCount + 1)
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.firebutton"
        const val KEY_FIREBUTTON_USE_COUNT = "FIREBUTTON_USE_COUNT"
    }
}
