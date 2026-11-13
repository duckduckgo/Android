/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.devicemigration.impl

import android.annotation.SuppressLint
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_RESTORED_FROM_BACKUP
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_TRANSFER_ALLOWED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.LOCAL_FILE
import java.io.File

/**
 * Leaves the definite "this install was restored" signal that [RealDeviceMigration] prefers over its heuristics.
 *
 * The framework runs this agent in restricted mode, with the base Application instead of ours, so there is no DI graph here:
 * only plain Android APIs, and nothing that could fail the user's transfer. Backup itself is left to the default implementation
 * so the XML rules stay the single source of what transfers, except that nothing is sent while the remote flag is off: the new
 * phone then starts as a fresh install, with nothing to detect and nothing to act on later.
 */
class DeviceMigrationBackupAgent : BackupAgent() {

    private val tally = RestoreTally()

    // Full-data apps never use key-value backup (fullBackupOnly in the manifest); these stay empty.
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (shouldSendNothing(this)) return
        super.onFullBackup(data)
    }

    override fun onRestoreFile(data: ParcelFileDescriptor, size: Long, destination: File?, type: Int, mode: Long, mtime: Long) {
        super.onRestoreFile(data, size, destination, type, mode, mtime)
        tally.record(destination)
    }

    override fun onRestoreFinished() {
        recordRestore(this, tally.restoredFileCount)
    }

    /** Counts the files a restore actually wrote. */
    internal class RestoreTally {
        var restoredFileCount = 0
            private set

        // A null destination is a file the rules refused; the framework still hands it over so its bytes can be skipped.
        fun record(destination: File?) {
            if (destination != null) restoredFileCount++
        }
    }

    internal companion object {
        /**
         * Samsung Smart Switch backups reach the agent without the device-transfer flag, so the transport flags cannot tell a
         * transfer from a cloud backup: the remote flag holds back every backup, and the rules alone keep cloud backup empty.
         * A phone that never saved the flag, or whose file cannot be read, sends nothing: the app has not started on this version
         * yet, so whether transfers are allowed is not known.
         */
        @SuppressLint("DenyListedApi") // no SharedPreferencesProvider in restricted mode; the app writes this file as plain prefs
        fun shouldSendNothing(context: Context): Boolean {
            return try {
                !context.getSharedPreferences(LOCAL_FILE, Context.MODE_PRIVATE).getBoolean(KEY_TRANSFER_ALLOWED, false)
            } catch (e: Exception) {
                true
            }
        }

        /**
         * An empty restore is not a transfer: cloud backup excludes all data today, yet the framework may still run a restore.
         * The write is synchronous because the process is killed as soon as the restore ends.
         */
        @SuppressLint("DenyListedApi", "ApplySharedPref") // no SharedPreferencesProvider in restricted mode; the app reads this file as plain prefs
        fun recordRestore(context: Context, restoredFileCount: Int) {
            if (restoredFileCount == 0) return
            try {
                context.getSharedPreferences(LOCAL_FILE, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_RESTORED_FROM_BACKUP, true)
                    .commit()
            } catch (e: Exception) {
                // Detection falls back to its heuristics; a crash here would fail the whole restore.
            }
        }
    }
}
