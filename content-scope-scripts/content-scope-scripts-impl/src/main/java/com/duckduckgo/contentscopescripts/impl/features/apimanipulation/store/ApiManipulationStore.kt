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

package com.duckduckgo.contentscopescripts.impl.features.apimanipulation.store

import android.annotation.SuppressLint
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ApiManipulationStore {
    suspend fun insertJsonData(jsonData: String): Boolean
    suspend fun getJsonData(): String?
}

private const val FILENAME: String = "com.duckduckgo.contentscopescripts.apimanipulation.store"
private const val KEY_JSON_DATA: String = "json_data"

@ContributesBinding(AppScope::class)
class RealApiManipulationStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : ApiManipulationStore {
    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    override suspend fun getJsonData(): String? {
        return preferences.getString(KEY_JSON_DATA, null)
    }

    @SuppressLint("UseKtx")
    override suspend fun insertJsonData(jsonData: String): Boolean {
        return preferences.edit().putString(KEY_JSON_DATA, jsonData).commit()
    }
}
