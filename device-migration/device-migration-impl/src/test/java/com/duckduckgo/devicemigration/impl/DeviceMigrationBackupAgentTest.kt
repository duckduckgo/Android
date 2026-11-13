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
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_RESTORED_FROM_BACKUP
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_TRANSFER_ALLOWED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.LOCAL_FILE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceMigrationBackupAgentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Reads the plain file directly, as the app does through SharedPreferencesProvider once the agent has written it.
    @get:SuppressLint("DenyListedApi")
    private val local get() = context.getSharedPreferences(LOCAL_FILE, Context.MODE_PRIVATE)

    @Test
    fun whenRestoreWritesFilesThenTallyCountsThem() {
        val tally = DeviceMigrationBackupAgent.RestoreTally()

        tally.record(File("app.db"))
        tally.record(File("shared_prefs/settings.xml"))

        assertEquals(2, tally.restoredFileCount)
    }

    @Test
    fun whenRulesRefuseFilesThenTallyLeavesThemOut() {
        val tally = DeviceMigrationBackupAgent.RestoreTally()

        tally.record(null)
        tally.record(File("app.db"))
        tally.record(null)

        assertEquals(1, tally.restoredFileCount)
    }

    @Test
    fun whenNothingRestoredThenTallyIsZero() {
        assertEquals(0, DeviceMigrationBackupAgent.RestoreTally().restoredFileCount)
    }

    @Test
    fun whenRestoreWroteFilesThenMarkerRecorded() {
        DeviceMigrationBackupAgent.recordRestore(context, restoredFileCount = 3)

        assertTrue(local.getBoolean(KEY_RESTORED_FROM_BACKUP, false))
    }

    @Test
    fun whenRestoreWroteNoFilesThenNoMarker() {
        DeviceMigrationBackupAgent.recordRestore(context, restoredFileCount = 0)

        assertFalse(local.contains(KEY_RESTORED_FROM_BACKUP))
    }

    @Test
    fun whenTransferNotAllowedThenBackupSendsNothing() {
        local.edit().putBoolean(KEY_TRANSFER_ALLOWED, false).commit()

        assertTrue(DeviceMigrationBackupAgent.shouldSendNothing(context))
    }

    @Test
    fun whenTransferAllowedThenBackupSendsData() {
        local.edit().putBoolean(KEY_TRANSFER_ALLOWED, true).commit()

        assertFalse(DeviceMigrationBackupAgent.shouldSendNothing(context))
    }

    @Test
    fun whenFlagNeverSavedThenBackupSendsNothing() {
        assertTrue(DeviceMigrationBackupAgent.shouldSendNothing(context))
    }
}
