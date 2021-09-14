/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.migrations

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Singleton

interface MigrationStore {
    var version: Int
}

@ContributesBinding(AppObjectGraph::class)
@Singleton
class MigrationSharedPreferences @Inject constructor(private val context: Context) : MigrationStore {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override var version: Int
        get() = preferences.getInt(KEY_VERSION, 0)
        set(version) = preferences.edit { putInt(KEY_VERSION, version) }

    companion object {
        const val FILENAME = "com.duckduckgo.app.global.migrations"
        const val KEY_VERSION = "KEY_VERSION"
    }
}
