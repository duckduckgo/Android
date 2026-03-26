package com.duckduckgo.autofill.impl.ui.credential.management.importpassword

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_RESTARTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_STARTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.ImportPasswordsViaDesktopSyncDataStore
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.desktopapp.UserJourneyEndRecorder
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ImportPasswordsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val importPasswordsDataStore: ImportPasswordsViaDesktopSyncDataStore = mock()
    private val deviceSyncState: DeviceSyncState = mock()
    private val userJourneyEndRecorder: UserJourneyEndRecorder = mock()
    private val pixel: Pixel = mock()
    private val testee = ImportPasswordsViewModel(
        pixel = pixel,
        deviceSyncState = deviceSyncState,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        importPasswordsDataStore = importPasswordsDataStore,
        userJourneyEndRecorder = userJourneyEndRecorder,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenUserLeavesScreenWithoutTakingActionThenNoActionPixelSent() {
        testee.userLeavingScreen()
        verify(pixel).fire(AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION)
    }

    @Test
    fun whenUserLeavesScreenAfterClickingDesktopAppButtonThenNoActionPixelNotSent() {
        testee.onUserClickedGetDesktopAppButton()
        testee.userLeavingScreen()
        verify(pixel, never()).fire(AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION)
    }

    @Test
    fun whenUserLeavesScreenAfterClickingSyncButtonThenNoActionPixelNotSent() {
        testee.onUserClickedSyncWithDesktopButton()
        testee.userLeavingScreen()
        verify(pixel, never()).fire(AUTOFILL_IMPORT_PASSWORDS_USER_TOOK_NO_ACTION)
    }

    @Test
    fun whenUserLaunchesScreenWithNoUserJourneyActiveAndNotAlreadySyncingThenOnlyStartJourneyPixelSent() = runTest {
        configureSignedOut()
        configureNoUserJourneyActive()
        testee.userLaunchedScreen()
        verify(pixel).fire(AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_STARTED)
        verify(pixel, never()).fire(AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_RESTARTED)
        verify(importPasswordsDataStore).startUserJourney()
    }

    @Test
    fun whenUserLaunchesScreenWithUserJourneyAlreadyActiveAndNotAlreadySyncingThenRestartPixelSent() = runTest {
        configureSignedOut()
        configureUserJourneyActive()
        testee.userLaunchedScreen()
        verify(pixel).fire(AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_RESTARTED)
        verify(pixel, never()).fire(AUTOFILL_IMPORT_PASSWORDS_USER_JOURNEY_STARTED)
        verify(importPasswordsDataStore).startUserJourney()
    }

    @Test
    fun whenUserLaunchesScreenWithUserJourneyAlreadyActiveAndAlreadySyncingThenXXX() = runTest {
        configureSignedOut()
        configureUserJourneyActive()
        configureSignedInWithDesktopDevice()
        testee.userLaunchedScreen()
        verifyNoInteractions(pixel)
        verify(importPasswordsDataStore, never()).startUserJourney()
        verify(userJourneyEndRecorder).recordSuccessfulJourney()
    }

    private suspend fun configureNoUserJourneyActive() {
        whenever(importPasswordsDataStore.getUserJourneyStartTime()).thenReturn(null)
    }
    private suspend fun configureUserJourneyActive() {
        whenever(importPasswordsDataStore.getUserJourneyStartTime()).thenReturn(0)
    }

    @Test
    fun whenUserReturnsToScreenWithoutHavingTakenActionAndWithoutHavingInitiatedJourneyThenNoJourneyEndRecorded() = runTest {
        testee.userLaunchedScreen()
        testee.userLaunchedScreen()
        verifyNoInteractions(userJourneyEndRecorder)
    }

    @Test
    fun whenUserReturnsToScreenWithoutHavingInitiatedUserJourneyThenNoJourneyEndRecorded() = runTest {
        configureSignedInWithDesktopDevice()
        testee.userLaunchedScreen()
        testee.onUserClickedSyncWithDesktopButton()

        // signed in, but user journey wasn't initiated
        testee.userLaunchedScreen()
        verifyNoInteractions(userJourneyEndRecorder)
    }

    @Test
    fun whenSignedInUserReturnsToScreenWithoutHavingTakenAnActionThenNoJourneyEndRecorded() = runTest {
        configureSignedOut()
        testee.userLaunchedScreen()
        configureSignedInWithDesktopDevice()

        // signed in, but user did not take an action to start their journey
        testee.userLaunchedScreen()
        verifyNoInteractions(userJourneyEndRecorder)
    }

    @Test
    fun whenUserReturnsToScreenAfterSyncingDesktopDeviceThenUserJourneyRecordedAsSuccessful() = runTest {
        startImportPasswordJourneyFromSignedOutState()

        // user returns to screen after syncing a desktop device; this counts as a success
        configureSignedInWithDesktopDevice()
        testee.userReturnedFromSyncSettings()

        verify(userJourneyEndRecorder).recordSuccessfulJourney()
    }

    @Test
    fun whenUserReturnsToScreenAfterSyncingMobileDeviceThenNoUserJourneyRecordedAsSuccessful() = runTest {
        startImportPasswordJourneyFromSignedOutState()

        // user returns to screen after syncing a mobile device; doesn't count as a success
        configureSignedInWithMobileDevice()
        testee.userLaunchedScreen()

        verifyNoInteractions(userJourneyEndRecorder)
    }

    // start signed out, launch the screen and simulate taking an action to start the journey
    private suspend fun startImportPasswordJourneyFromSignedOutState() {
        configureSignedOut()
        testee.userLaunchedScreen()
        whenever(importPasswordsDataStore.getUserJourneyStartTime()).thenReturn(0L)
        testee.onUserClickedGetDesktopAppButton()
    }

    private fun configureSignedInWithMobileDevice() {
        val syncState = SignedIn("testUserId", listOf(aDevice(MOBILE)))
        whenever(deviceSyncState.getAccountState()).thenReturn(syncState)
    }

    private fun configureSignedInWithDesktopDevice() {
        val syncState = SignedIn("testUserId", listOf(aDevice(DESKTOP)))
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
