/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.statistics.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class BackupSharedPreferences @Inject constructor(private val context: Context) : BackupDataStore {

    override var oldAtb: Atb?
        get() {
            val atbString = preferences.getString(KEY_ATB, null) ?: return null
            return Atb(atbString)
        }
        set(atb) = preferences.edit { putString(KEY_ATB, atb?.version) }


    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    companion object {
        private const val FILENAME = "com.duckduckgo.app.backup"
        private const val KEY_ATB = "com.duckduckgo.app.backup.atb"
    }
}
