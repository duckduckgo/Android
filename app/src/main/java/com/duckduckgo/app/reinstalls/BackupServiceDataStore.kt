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

package com.duckduckgo.app.reinstalls

import android.content.Context
import android.content.SharedPreferences
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface BackupServiceDataStore {
    fun clearBackupPreferences()
}

@ContributesBinding(AppScope::class)
class BackupServiceSharedPreferences @Inject constructor(
    private val context: Context,
) : BackupServiceDataStore {

    private val backupServicePreferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME_BACKUP_SERVICE, Context.MODE_PRIVATE) }
    private val backupPixelPreferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME_BACKUP_PIXEL, Context.MODE_PRIVATE) }

    override fun clearBackupPreferences() {
        backupServicePreferences.edit().clear().apply()
        backupPixelPreferences.edit().clear().apply()
    }

    companion object {
        private const val FILENAME_BACKUP_SERVICE = "com.duckduckgo.app.statistics.backup"
        private const val FILENAME_BACKUP_PIXEL = "com.duckduckgo.app.statistics.backup.pixel"
    }
}
