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

package com.duckduckgo.app.backup.agent.impl

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataOutput
import android.app.backup.SharedPreferencesBackupHelper
import android.os.ParcelFileDescriptor
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.backup.agent.impl.pixel.BackupAgentPixelName.BACKUP_SERVICE_ENABLED
import com.duckduckgo.app.backup.agent.impl.store.BackupPixelDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.di.scopes.BackupAgentScope
import dagger.android.AndroidInjection
import javax.inject.Inject

@InjectWith(BackupAgentScope::class)
class DuckDuckGoBackupAgent : BackupAgentHelper() {

    @Inject lateinit var pixel: Pixel

    @Inject lateinit var backupPixelDataStore: BackupPixelDataStore

    @Inject lateinit var statisticsDataStore: StatisticsDataStore

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this, this)
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
        if (!backupPixelDataStore.backupEnabledPixelSent) {
            pixel.fire(BACKUP_SERVICE_ENABLED)
            backupPixelDataStore.backupEnabledPixelSent = true
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.app.statistics.backup"
        const val FILENAME_BACKUP = "com.duckduckgo.app.backup.backup"
    }
}
