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

import android.content.SharedPreferences
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_IS_NEW_INSTALL
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_LAUNCHED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SEEN
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.LOCAL_FILE
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.TRANSFERRED_FILE
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class RealDeviceMigrationTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val prefsByName = mutableMapOf<String, SharedPreferences>()
    private val provider = object : SharedPreferencesProvider by FakeSharedPreferencesProvider() {
        override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean): SharedPreferences =
            prefsByName.getOrPut(name) { InMemorySharedPreferences() }
    }
    private var unupdatedSinceFirstInstall = true

    private val transferred get() = provider.getSharedPreferences(TRANSFERRED_FILE)
    private val local get() = provider.getSharedPreferences(LOCAL_FILE)

    private val testee by lazy {
        RealDeviceMigration(
            markerStore = DeviceMigrationMarkerStore(provider),
            installTimeComparison = object : InstallTimeComparison {
                override fun isUnupdatedSinceFirstInstall() = unupdatedSinceFirstInstall
            },
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    private fun onCreate() = testee.onCreate(mock<LifecycleOwner>())

    @Test
    fun whenNoMarkersThenNotMigratedAndMarkersWritten() = runTest {
        assertFalse(testee.isMigratedInstall())
        assertFalse(testee.isMigrationFlowPending())

        onCreate()

        assertTrue(local.getBoolean(KEY_LAUNCHED, false))
        assertTrue(transferred.getBoolean(KEY_SEEN, false))
    }

    @Test
    fun whenTransferredMarkerAndNoLocalMarkerThenMigrated() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_IS_NEW_INSTALL, false).commit()

        assertTrue(testee.isMigratedInstall())
        assertTrue(testee.isMigrationFlowPending())
        assertEquals(false, testee.sourceDeviceIsNewInstall())
    }

    @Test
    fun whenBothMarkersThenNotMigrated() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_IS_NEW_INSTALL, false).commit()
        local.edit().putBoolean(KEY_LAUNCHED, true).commit()

        assertFalse(testee.isMigratedInstall())
        assertNull(testee.sourceDeviceIsNewInstall())
    }

    @Test
    fun whenMigratedThenOnCreateDoesNotLoseSourceValue() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_IS_NEW_INSTALL, false).commit()

        onCreate()

        assertEquals(false, testee.sourceDeviceIsNewInstall())
    }

    @Test
    fun whenChainedMigrationThenSourceValueOfFirstPhoneKept() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_IS_NEW_INSTALL, true).commit()

        assertEquals(true, testee.sourceDeviceIsNewInstall())
        onCreate()

        assertTrue(transferred.getBoolean(KEY_IS_NEW_INSTALL, false))
    }

    @Test
    fun whenChainedMigrationAndPackageUpdatedThenTransferredIsNewInstallFalse() = runTest {
        unupdatedSinceFirstInstall = false
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_IS_NEW_INSTALL, true).commit()

        onCreate()

        assertFalse(transferred.getBoolean(KEY_IS_NEW_INSTALL, true))
    }

    @Test
    fun whenFlowCompletedThenNotPending() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_IS_NEW_INSTALL, false).commit()
        assertTrue(testee.isMigrationFlowPending())

        testee.markMigrationFlowCompleted()

        assertFalse(testee.isMigrationFlowPending())
        assertTrue(testee.isMigratedInstall())
    }
}
