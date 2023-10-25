/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.experiments.impl.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ExperimentsDataStore {
    var variantManagerConfigDownloaded: Boolean
}

@ContributesBinding(AppScope::class)
class ExperimentsSharedPreferences @Inject constructor(private val context: Context) : ExperimentsDataStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var variantManagerConfigDownloaded: Boolean
        get() = preferences.getBoolean(KEY_VARIANT_MANAGER_DOWNLOADED, false)
        set(downloaded) = preferences.edit { putBoolean(KEY_VARIANT_MANAGER_DOWNLOADED, downloaded) }

    companion object {
        const val FILENAME = "com.duckduckgo.experiments.settings"
        const val KEY_VARIANT_MANAGER_DOWNLOADED = "VARIANT_MANAGER_DOWNLOADED"
    }
}


