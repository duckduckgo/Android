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

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName.BACKUP_SERVICE_ENABLED
import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.di.scopes.BackupAgentScope
import dagger.android.AndroidInjection
import javax.inject.Inject

@InjectWith(BackupAgentScope::class)
class DuckDuckGoBackupAgent : BackupAgent() {

    @Inject lateinit var pixel: Pixel

    @Inject lateinit var appProperties: AppProperties

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this, this)
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) {
        val buffer: ByteArray = appProperties.atb().toByteArray()
        val len = buffer.size
        data?.writeEntityHeader(BACKUP_KEY, len)
        data?.writeEntityData(buffer, len)
        pixel.fire(BACKUP_SERVICE_ENABLED)
    }

    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor) {
        while (data.readNextHeader()) {
            val key: String = data.key
            val dataSize: Int = data.dataSize
            if (BACKUP_KEY == key) {
                val buffer = ByteArray(dataSize)
                data.readEntityData(buffer, 0, dataSize)
                val oldAtb = String(buffer)
                appProperties.storeOldAtb(oldAtb)
            }
        }
    }

    companion object {
        const val BACKUP_KEY = "oldAtb"
    }
}
