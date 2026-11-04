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

package com.duckduckgo.networkprotection.impl.devicemigration

import android.content.SharedPreferences
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.networkprotection.impl.store.RealNetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.volume.NetpDataVolumeStore.DataVolume
import com.duckduckgo.networkprotection.impl.volume.RealNetpDataVolumeStore
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import com.duckduckgo.networkprotection.store.RealNetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.RealNetworkProtectionPrefs
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MigratedInstallNetpStateCleanupTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val doneOnThisInstall = mutableSetOf<String>()
    private val deviceMigration: DeviceMigration = mock {
        on { isDoneOnThisInstall(any()) } doAnswer { it.getArgument<String>(0) in doneOnThisInstall }
        on { markDoneOnThisInstall(any()) } doAnswer { doneOnThisInstall += it.getArgument<String>(0) }
    }
    private val preferenceFiles = mutableMapOf<String, SharedPreferences>()
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doAnswer { invocation ->
            preferenceFiles.getOrPut(invocation.getArgument(0)) { RecordingSharedPreferences() }
        }
    }
    private val networkProtectionPrefs = RealNetworkProtectionPrefs(sharedPreferencesProvider)
    private val geoswitchingRepository = RealNetPGeoswitchingRepository(networkProtectionPrefs, mock(), coroutineTestRule.testDispatcherProvider)
    private val networkProtectionRepository = RealNetworkProtectionRepository(networkProtectionPrefs)
    private val dataVolumeStore = RealNetpDataVolumeStore(networkProtectionPrefs)

    private val testee = MigratedInstallNetpStateCleanup(
        deviceMigration = deviceMigration,
        geoswitchingRepository = geoswitchingRepository,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Before
    fun setup() = runTest {
        geoswitchingRepository.setUserPreferredLocation(PREFERRED_LOCATION)
        networkProtectionRepository.enabledTimeInMillis = PREVIOUS_PHONE_ENABLED_TIME
        dataVolumeStore.dataVolume = PREVIOUS_PHONE_DATA_VOLUME
        networkProtectionPrefs.putString(LEGACY_PRIVATE_KEY, "previous-phone-key")
    }

    @Test
    fun whenMigratedInstallThenPreviousPhoneTunnelStateCleared() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        startApp()

        assertEquals(-1L, networkProtectionRepository.enabledTimeInMillis)
        assertEquals(DataVolume(), dataVolumeStore.dataVolume)
        assertEquals(null, networkProtectionPrefs.getString(LEGACY_PRIVATE_KEY, null))
    }

    @Test
    fun whenMigratedInstallThenPreferredLocationKept() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        startApp()

        assertEquals(PREFERRED_LOCATION, geoswitchingRepository.getUserPreferredLocation())
    }

    @Test
    fun whenNotMigratedInstallThenNothingCleared() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(false)

        startApp()

        assertEquals(PREVIOUS_PHONE_ENABLED_TIME, networkProtectionRepository.enabledTimeInMillis)
        assertEquals(PREVIOUS_PHONE_DATA_VOLUME, dataVolumeStore.dataVolume)
        assertEquals(PREFERRED_LOCATION, geoswitchingRepository.getUserPreferredLocation())
    }

    @Test
    fun whenCleanupAlreadyDoneThenStateWrittenOnThisPhoneIsKept() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        startApp()

        networkProtectionRepository.enabledTimeInMillis = THIS_PHONE_ENABLED_TIME
        startApp()

        assertEquals(THIS_PHONE_ENABLED_TIME, networkProtectionRepository.enabledTimeInMillis)
    }

    @Test
    fun whenMigratedInstallCheckThrowsThenRetriedOnNextStart() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenAnswer { throw RuntimeException("boom") }.thenReturn(true)

        startApp()
        assertEquals(PREVIOUS_PHONE_ENABLED_TIME, networkProtectionRepository.enabledTimeInMillis)

        startApp()
        assertEquals(-1L, networkProtectionRepository.enabledTimeInMillis)
    }

    @Test
    fun guardIsNotStoredInTheTransferredPreferencesFile() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)

        startApp()

        assertFalse(preferenceFiles.getValue(NETP_PREFS_FILE).all.keys.any { it.contains("cleanup", ignoreCase = true) })
        assertTrue(doneOnThisInstall.isNotEmpty())
    }

    @Test
    fun whenMigratedInstallThenPreviousPhoneStateReplacedInOneCommittedWrite() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        val netpFile = preferenceFiles.getValue(NETP_PREFS_FILE) as RecordingSharedPreferences
        netpFile.resetCounts()

        startApp()

        assertEquals(1, netpFile.commits)
        assertEquals(0, netpFile.applies)
    }

    @Test
    fun whenMigrationKillSwitchOffThenCleanupStillRuns() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(deviceMigration.isMigrationExperienceEnabled()).thenReturn(false)

        startApp()

        assertEquals(-1L, networkProtectionRepository.enabledTimeInMillis)
        assertEquals(PREFERRED_LOCATION, geoswitchingRepository.getUserPreferredLocation())
    }

    private fun startApp() {
        testee.onCreate(mock())
        coroutineTestRule.testScope.advanceUntilIdle()
    }

    private companion object {
        const val NETP_PREFS_FILE = "com.duckduckgo.networkprotection.store.prefs.v1"
        const val LEGACY_PRIVATE_KEY = "wg_private_key"
        const val PREVIOUS_PHONE_ENABLED_TIME = 1_000L
        const val THIS_PHONE_ENABLED_TIME = 2_000L
        val PREVIOUS_PHONE_DATA_VOLUME = DataVolume(receivedBytes = 10, transmittedBytes = 20)
        val PREFERRED_LOCATION = UserPreferredLocation(countryCode = "nl", cityName = "Amsterdam")
    }
}

/** Counts how edits are written, to check that a replacement cannot be half applied. */
private class RecordingSharedPreferences(
    private val delegate: InMemorySharedPreferences = InMemorySharedPreferences(),
) : SharedPreferences by delegate {
    var commits = 0
        private set
    var applies = 0
        private set

    fun resetCounts() {
        commits = 0
        applies = 0
    }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by delegate.edit() {
        override fun commit(): Boolean {
            commits++
            return true
        }

        override fun apply() {
            applies++
        }
    }
}
