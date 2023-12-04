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

package com.duckduckgo.app.backup.agent.impl.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface BackupPixelDataStore {
    var backupEnabledPixelSent: Boolean
}

@ContributesBinding(AppScope::class)
class BackupPixelSharedPreferences @Inject constructor(
    private val context: Context,
) : BackupPixelDataStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var backupEnabledPixelSent: Boolean
        get() = preferences.getBoolean(KEY_PIXEL_SENT, false)
        set(value) = preferences.edit { putBoolean(KEY_PIXEL_SENT, value) }

    companion object {
        private const val FILENAME = "com.duckduckgo.app.statistics.backup.pixel"
        private const val KEY_PIXEL_SENT = "com.duckduckgo.app.statistics.backup.pixel.sent"
    }
}
