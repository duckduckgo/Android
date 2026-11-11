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
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_DEFAULT_BROWSER
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_IS_NEW_INSTALL
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_LAUNCHED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_MIGRATED
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_RESTORED_FROM_BACKUP
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SEEN
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SIGNED_IN_TO_SYNC
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SOURCE_DEFAULT_BROWSER
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SOURCE_SIGNED_IN_TO_SYNC
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.LOCAL_FILE
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.TRANSFERRED_FILE
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doAnswer
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
    private val feature = FakeFeatureToggleFactory.create(DeviceMigrationFeature::class.java)
    private var hasAtb = false
    private var signedInToSync = false
    private val deviceSyncState: DeviceSyncState = mock {
        on { isUserSignedInOnDevice() } doAnswer { signedInToSync }
    }
    private var isDefaultBrowser = false
    private val defaultBrowserDetector: DefaultBrowserDetector = mock {
        on { isDefaultBrowser() } doAnswer { isDefaultBrowser }
    }
    private val statisticsDataStore: StatisticsDataStore = mock {
        on { hasInstallationStatistics } doAnswer { hasAtb }
    }

    private val transferred get() = provider.getSharedPreferences(TRANSFERRED_FILE)
    private val local get() = provider.getSharedPreferences(LOCAL_FILE)

    private val testee by lazy { newInstance() }

    private fun newInstance() = RealDeviceMigration(
        markerStore = DeviceMigrationMarkerStore(provider),
        installTimeComparison = object : InstallTimeComparison {
            override fun isUnupdatedSinceFirstInstall() = unupdatedSinceFirstInstall
        },
        deviceMigrationFeature = feature,
        statisticsDataStore = statisticsDataStore,
        deviceSyncState = lazyOf(deviceSyncState),
        defaultBrowserDetector = lazyOf(defaultBrowserDetector),
        dispatchers = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    private fun onCreate() = testee.onCreate(mock<LifecycleOwner>())

    private fun onResume() = testee.onResume(mock<LifecycleOwner>())

    @Test
    fun whenKeyNotMarkedThenNotDoneOnThisInstall() {
        assertFalse(testee.isDoneOnThisInstall("feature.step"))
    }

    @Test
    fun whenKeyMarkedThenDoneOnThisInstallAcrossInstances() {
        testee.markDoneOnThisInstall("feature.step")

        assertTrue(newInstance().isDoneOnThisInstall("feature.step"))
        assertFalse(newInstance().isDoneOnThisInstall("feature.other"))
    }

    @Test
    fun whenKeyMarkedThenStoredInTheFileThatNeverTransfers() {
        testee.markDoneOnThisInstall("feature.step")

        assertTrue(local.all.keys.any { it.endsWith("feature.step") })
        assertFalse(transferred.all.keys.any { it.endsWith("feature.step") })
    }

    @Test
    fun whenKeyMarkedThenDetectionKeysAreUntouched() {
        testee.markDoneOnThisInstall(KEY_LAUNCHED)

        assertFalse(local.getBoolean(KEY_LAUNCHED, false))
    }

    @Test
    fun whenNoMarkersThenNotMigratedAndMarkersWritten() = runTest {
        assertFalse(testee.isMigratedInstall())

        onCreate()

        assertTrue(local.getBoolean(KEY_LAUNCHED, false))
        assertTrue(transferred.getBoolean(KEY_SEEN, false))
    }

    @Test
    fun whenTransferredMarkerAndNoLocalMarkerThenMigrated() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_IS_NEW_INSTALL, false).commit()

        assertTrue(testee.isMigratedInstall())
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
    fun whenMigratedThenFlowPendingUntilCompleted() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).commit()

        assertTrue(testee.isMigrationFlowPending())
        testee.markMigrationFlowCompleted()

        assertFalse(testee.isMigrationFlowPending())
        assertFalse(newInstance().isMigrationFlowPending())
    }

    @Test
    fun whenNotMigratedThenFlowNotPending() = runTest {
        assertFalse(testee.isMigrationFlowPending())
    }

    @Test
    fun whenMigratedBeforeTheFlowExistedThenFlowNotPending() = runTest {
        local.edit().putBoolean(KEY_LAUNCHED, true).putBoolean(KEY_MIGRATED, true).commit()

        assertTrue(testee.isMigratedInstall())
        assertFalse(testee.isMigrationFlowPending())
    }

    @Test
    fun whenPreviousPhoneRecordedDefaultBrowserThenReported() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_DEFAULT_BROWSER, true).commit()

        assertEquals(true, testee.sourceDeviceWasDefaultBrowser())
    }

    @Test
    fun whenPreviousPhoneNeverRecordedDefaultBrowserThenUnknown() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).commit()

        assertNull(testee.sourceDeviceWasDefaultBrowser())
    }

    @Test
    fun whenNotMigratedThenSourceDefaultBrowserUnknown() = runTest {
        transferred.edit().putBoolean(KEY_DEFAULT_BROWSER, true).commit()

        assertNull(testee.sourceDeviceWasDefaultBrowser())
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
    fun whenFeatureEnabledThenMigrationExperienceEnabled() = runTest {
        feature.self().setRawStoredState(State(enable = true))

        assertTrue(testee.isMigrationExperienceEnabled())
    }

    @Test
    fun whenFeatureDisabledThenMigrationExperienceDisabledAndDetectionUnaffected() = runTest {
        feature.self().setRawStoredState(State(enable = false))
        transferred.edit().putBoolean(KEY_SEEN, true).commit()

        assertFalse(testee.isMigrationExperienceEnabled())
        assertTrue(testee.isMigratedInstall())
    }

    @Test
    fun whenFreshInstallWithoutMarkersOrAtbThenNotMigrated() = runTest {
        assertFalse(testee.isMigratedInstall())
        assertNull(testee.sourceDeviceIsNewInstall())
    }

    @Test
    fun whenFreshInstallWithoutMarkersButWithAtbThenMigratedFromEstablishedPhone() = runTest {
        hasAtb = true

        assertTrue(testee.isMigratedInstall())
        assertEquals(false, testee.sourceDeviceIsNewInstall())
    }

    @Test
    fun whenAtbFallbackDetectedThenStillMigratedAfterRestart() = runTest {
        hasAtb = true
        assertTrue(testee.isMigratedInstall())
        onCreate()

        val restarted = newInstance()

        assertTrue(restarted.isMigratedInstall())
        assertEquals(false, restarted.sourceDeviceIsNewInstall())
        assertFalse(transferred.getBoolean(KEY_IS_NEW_INSTALL, true))
    }

    @Test
    fun whenUpdatedInstallWithoutMarkersButWithAtbThenNotMigrated() = runTest {
        unupdatedSinceFirstInstall = false
        hasAtb = true

        assertFalse(testee.isMigratedInstall())
        assertNull(testee.sourceDeviceIsNewInstall())
    }

    @Test
    fun whenRestoredFromBackupAndUpdatedBeforeFirstOpenThenMigratedFromEstablishedPhone() = runTest {
        unupdatedSinceFirstInstall = false
        local.edit().putBoolean(KEY_RESTORED_FROM_BACKUP, true).commit()

        assertTrue(testee.isMigratedInstall())
        assertEquals(false, testee.sourceDeviceIsNewInstall())
    }

    @Test
    fun whenRestoredFromBackupWithTransferredMarkerThenTransferredSourceStateWins() = runTest {
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_IS_NEW_INSTALL, true).commit()
        local.edit().putBoolean(KEY_RESTORED_FROM_BACKUP, true).commit()

        assertTrue(testee.isMigratedInstall())
        assertEquals(true, testee.sourceDeviceIsNewInstall())
    }

    @Test
    fun whenRestoredFromBackupThenStillMigratedAfterRestart() = runTest {
        local.edit().putBoolean(KEY_RESTORED_FROM_BACKUP, true).commit()
        assertTrue(testee.isMigratedInstall())
        onCreate()

        assertTrue(newInstance().isMigratedInstall())
    }

    @Test
    fun whenAtbWrittenAfterBeforeAtbInitThenFreshInstallNotMigrated() = runTest {
        testee.beforeAtbInit()
        hasAtb = true

        assertFalse(testee.isMigratedInstall())
        assertFalse(newInstance().isMigratedInstall())
    }

    @Test
    fun whenSignedInToSyncThenOnCreateStampsItForTheNextPhone() = runTest {
        signedInToSync = true

        onCreate()

        assertTrue(transferred.getBoolean(KEY_SIGNED_IN_TO_SYNC, false))
    }

    @Test
    fun whenSyncTurnedOffBetweenStartsThenStampRefreshed() = runTest {
        signedInToSync = true
        onCreate()

        signedInToSync = false
        newInstance().onCreate(mock())

        assertFalse(transferred.getBoolean(KEY_SIGNED_IN_TO_SYNC, true))
    }

    @Test
    fun whenThisPhoneStampsItsOwnSyncStateThenPreviousPhoneStampKept() = runTest {
        transferFromPhone(signedInToSync = true)
        assertTrue(testee.isMigratedInstall())

        signedInToSync = false
        onCreate()

        assertTrue(local.getBoolean(KEY_SOURCE_SIGNED_IN_TO_SYNC, false))
        assertFalse(transferred.getBoolean(KEY_SIGNED_IN_TO_SYNC, true))
    }

    @Test
    fun whenDuckDuckGoIsDefaultThenOnCreateStampsItForTheNextPhone() = runTest {
        isDefaultBrowser = true

        onCreate()

        assertTrue(transferred.getBoolean(KEY_DEFAULT_BROWSER, false))
    }

    @Test
    fun whenDefaultBrowserChangedWhileAppRunningThenOnResumeRefreshesStamp() = runTest {
        onCreate()

        isDefaultBrowser = true
        onResume()

        assertTrue(transferred.getBoolean(KEY_DEFAULT_BROWSER, false))
    }

    @Test
    fun whenPreviousPhoneStampedDefaultBrowserThenKeptAfterThisPhoneStampsItsOwn() = runTest {
        transferFromPhone(signedInToSync = false)
        transferred.edit().putBoolean(KEY_DEFAULT_BROWSER, true).commit()
        assertTrue(testee.isMigratedInstall())

        isDefaultBrowser = false
        onCreate()

        assertTrue(local.getBoolean(KEY_SOURCE_DEFAULT_BROWSER, false))
        assertFalse(transferred.getBoolean(KEY_DEFAULT_BROWSER, true))
    }

    @Test
    fun whenPreviousPhoneLeftNoDefaultBrowserStampThenNoSourceValue() = runTest {
        transferFromPhone(signedInToSync = false)

        assertTrue(testee.isMigratedInstall())

        assertFalse(local.contains(KEY_SOURCE_DEFAULT_BROWSER))
    }

    private fun transferFromPhone(signedInToSync: Boolean) {
        transferred.edit()
            .putBoolean(KEY_SEEN, true)
            .putBoolean(KEY_IS_NEW_INSTALL, false)
            .putBoolean(KEY_SIGNED_IN_TO_SYNC, signedInToSync)
            .commit()
    }
}
