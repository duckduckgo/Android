package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.ConnectedDevice
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedOut
import com.duckduckgo.sync.api.DeviceSyncState.Type
import com.duckduckgo.sync.api.DeviceSyncState.Type.DESKTOP
import com.duckduckgo.sync.api.DeviceSyncState.Type.MOBILE
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ImportPasswordsUserJourneyLifecycleObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val dataStore: ImportPasswordsViaDesktopSyncDataStore = mock()
    private val deviceSyncState: DeviceSyncState = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val userJourneyEndRecorder: UserJourneyEndRecorder = mock()

    private val testee = ImportPasswordsUserJourneyLifecycleObserver(
        dataStore = dataStore,
        deviceSyncState = deviceSyncState,
        userJourneyEndRecorder = userJourneyEndRecorder,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenNoUserJourneyActiveThenNoUserJourneyEndRecorded() = runTest {
        configureUserJourneyAsNotActive()
        testee.onStart(lifecycleOwner)
        verifyNoInteractions(userJourneyEndRecorder)
    }

    @Test
    fun whenUserJourneyExpiredThenUserJourneyEndRecordedAsUnsuccessful() = runTest {
        configureUserJourneyAsExpired()
        testee.onStart(lifecycleOwner)
        verify(userJourneyEndRecorder).recordUnsuccessfulJourney()
    }

    @Test
    fun whenUserJourneyStillActiveButNotSignedIntoSyncThenNoUserJourneyEndRecorded() = runTest {
        configureUserJourneyAsStillActive()
        configureSignedOut()
        testee.onStart(lifecycleOwner)
        verifyNoInteractions(userJourneyEndRecorder)
    }

    @Test
    fun whenUserJourneyStillActiveAndSignedIntoSyncButNotWithADesktopDeviceThenNoUserJourneyEndRecorded() = runTest {
        configureUserJourneyAsStillActive()
        configureSignedInWithMobileDevice()
        testee.onStart(lifecycleOwner)
        verifyNoInteractions(userJourneyEndRecorder)
    }

    @Test
    fun whenUserJourneyStillActiveAndSignedIntoSyncWithADesktopDeviceThenUserJourneyEndRecordedAsSuccess() = runTest {
        configureUserJourneyAsStillActive()
        configureSignedInWithDesktopDevice()
        testee.onStart(lifecycleOwner)
        verify(userJourneyEndRecorder).recordSuccessfulJourney()
    }

    private suspend fun configureUserJourneyAsNotActive() {
        whenever(dataStore.getUserJourneyStartTime()).thenReturn(null)
    }

    private suspend fun configureUserJourneyAsExpired() {
        whenever(dataStore.getUserJourneyStartTime()).thenReturn(0)
    }

    private suspend fun configureUserJourneyAsStillActive() {
        whenever(dataStore.getUserJourneyStartTime()).thenReturn(System.currentTimeMillis() - 10)
    }

    private fun configureSignedInWithDesktopDevice() {
        val syncState = SignedIn("testUserId", listOf(aDevice(DESKTOP)))
        whenever(deviceSyncState.getAccountState()).thenReturn(syncState)
    }

    private fun configureSignedInWithMobileDevice() {
        val syncState = SignedIn("testUserId", listOf(aDevice(MOBILE)))
        whenever(deviceSyncState.getAccountState()).thenReturn(syncState)
    }

    private fun configureSignedOut() {
        whenever(deviceSyncState.getAccountState()).thenReturn(SignedOut)
    }

    private fun aDevice(type: Type): ConnectedDevice {
        return ConnectedDevice(
            deviceType = type,
            thisDevice = true,
            deviceName = "Test",
            deviceId = "1234",
        )
    }
}
