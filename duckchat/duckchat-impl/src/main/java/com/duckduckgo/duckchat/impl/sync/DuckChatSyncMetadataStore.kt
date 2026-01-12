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

package com.duckduckgo.duckchat.impl.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface DuckChatSyncMetadataStore {
    var deletionTimestamp: String?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDuckChatSyncMetadataStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : DuckChatSyncMetadataStore {

    override var deletionTimestamp: String?
        get() = preferences.getString(KEY_DELETION_TIMESTAMP, null)
        set(value) = preferences.edit(true) {
            if (value != null) {
                putString(KEY_DELETION_TIMESTAMP, value)
            } else {
                remove(KEY_DELETION_TIMESTAMP)
            }
        }

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = false,
            migrate = false,
        )
    }

    companion object {
        const val FILENAME = "com.duckduckgo.duckchat.sync.store"
        private const val KEY_DELETION_TIMESTAMP = "KEY_DELETION_TIMESTAMP"
    }
}
