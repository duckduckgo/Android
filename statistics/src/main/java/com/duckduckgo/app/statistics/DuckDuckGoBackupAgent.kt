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

package com.duckduckgo.app.statistics

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataOutput
import android.app.backup.SharedPreferencesBackupHelper
import android.os.ParcelFileDescriptor

class DuckDuckGoBackupAgent : BackupAgentHelper() {

    override fun onCreate() {
        super.onCreate()
        SharedPreferencesBackupHelper(this, FILENAME).also {
            addHelper(FILENAME_BACKUP, it)
        }
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) {
        super.onBackup(oldState, data, newState)
        // TODO add backup_service_enabled pixel
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.app.statistics"
        const val FILENAME_BACKUP = "com.duckduckgo.app.statistics.backup"
    }
}
