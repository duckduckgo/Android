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

package com.duckduckgo.savedsites.impl.sync.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface SavedSitesSyncEntitiesStore {
    var invalidEntitiesIds: List<String>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSavedSitesSyncEntitiesStore @Inject constructor(
    private val context: Context,
) : SavedSitesSyncEntitiesStore {

    override var invalidEntitiesIds: List<String>
        get() = preferences.getStringSet(KEY_CLIENT_INVALID_IDS, mutableSetOf())?.toList() ?: mutableListOf()
        set(value) {
            preferences.edit(true) {
                putStringSet(KEY_CLIENT_INVALID_IDS, value.toSet())
            }
        }

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.savedsites.sync.entities.store"
        private const val KEY_CLIENT_INVALID_IDS = "KEY_CLIENT_INVALID_IDS"
    }
}
