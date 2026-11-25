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

package com.duckduckgo.sync.impl.ui.dashboard

import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.TestSyncFixtures.connectedDevice
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.api.SyncAutoRestore
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncState.IN_PROGRESS
import com.duckduckgo.sync.api.SyncState.OFF
import com.duckduckgo.sync.api.SyncState.READY
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.SyncFeatureToggle
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckState
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckStateStore
import com.duckduckgo.sync.impl.auditlog.PendingLogin
import com.duckduckgo.sync.impl.auditlog.RealLoginAuditLogFeature
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.auth.FakeSyncDeviceAuthenticator
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.IntroRecoverSyncData
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.LaunchDeviceActivity
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.LaunchLearnMore
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.LaunchOriginalFlow
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.Command.ShowPreviousSessionReady
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.SetupFlows.CreateAccountFlow
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityViewModel.SetupFlows.SignInFlow
import com.duckduckgo.sync.impl.ui.dashboard.SyncDeviceListItem.SyncedDevice
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.lang.String.format
import java.time.Instant
import kotlin.reflect.KClass

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SyncActivityViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val recoveryPDF: RecoveryCodePDF = mock()
    private val clipboardInteractor: ClipboardInteractor = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val syncStateMonitor: SyncStateMonitor = mock()
    private val syncEngine: SyncEngine = mock()
    private val syncFeatureToggle: SyncFeatureToggle = mock()
    private val syncPixels: SyncPixels = mock()
    private val deviceAuthenticator = FakeSyncDeviceAuthenticator()
    private val syncSetupWideEvent: SyncSetupWideEvent = mock()
    private val syncAutoRestoreManager: SyncAutoRestoreManager = mock()
    private val syncAutoRestore: SyncAutoRestore = mock()
    private val fakeSyncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    private val fakeSettingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)

    private val stateFlow = MutableStateFlow(SyncState.READY)

    private val auditLogStateStore = FakeStateStore()

    private lateinit var testee: SyncActivityViewModel

    @Before
    fun before() = runTest {
        testee = SyncActivityViewModel(
            syncAccountRepository = syncAccountRepository,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            syncStateMonitor = syncStateMonitor,
            syncEngine = syncEngine,
            recoveryCodePDF = recoveryPDF,
            clipboard = clipboardInteractor,
            syncFeatureToggle = syncFeatureToggle,
            settingsPageFeature = fakeSettingsPageFeature,
            syncPixels = syncPixels,
            syncSetupWideEvent = syncSetupWideEvent,
            deviceAuthenticator = deviceAuthenticator,
            syncAutoRestoreManager = syncAutoRestoreManager,
            syncAutoRestore = syncAutoRestore,
            appCoroutineScope = coroutineTestRule.testScope,
            loginAuditLogFeature = RealLoginAuditLogFeature(fakeSyncFeature, coroutineTestRule.testDispatcherProvider),
            auditLogStateStore = auditLogStateStore,
        )
        whenever(syncStateMonitor.syncState()).thenReturn(emptyFlow())
        whenever(syncAccountRepository.isSyncSupported()).thenReturn(true)
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(false)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(false)
    }

    // Collecting viewState() starts the periodic device refresh, which loops on the test scheduler until the ViewModel is cleared.
    // runTest drains that scheduler when the body returns, so we cancel the ViewModel's scope first or the test never finishes.
    private fun runViewModelTest(testBody: suspend TestScope.() -> Unit) = runTest {
        try {
            testBody()
        } finally {
            testee.viewModelScope.cancel()
        }
    }

    @Test
    fun whenUserSignedInThenDeviceSyncViewStateIsEnabled() = runViewModelTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenShowAccount() = runViewModelTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)

            verify(syncEngine).triggerSync(FEATURE_READ)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenLoginQRCodeIsNotNull() = runViewModelTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)

            verify(syncEngine).triggerSync(FEATURE_READ)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserHasMultipleConnectedDevicesThenShowDevices() = runViewModelTest {
        givenAuthenticatedUser()

        val connectedDevices = listOf(connectedDevice, connectedDevice)
        whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(connectedDevices))

        testee.viewState().test {
            val initialState = expectMostRecentItem()
            assertEquals(connectedDevices.size, initialState.syncedDevices.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScreenOpenedThenSyncSettingsShownPixelFiredWithLaunchSource() = runViewModelTest {
        testee.onScreenOpened(launchSource = "typed_url_duck_com")

        verify(syncPixels).fireSyncSettingsShown("typed_url_duck_com")
    }

    @Test
    fun whenSyncThisDeviceThenBackupThisDeviceTappedPixelFired() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)

        testee.onSyncThisDevice(launchSource = "other")

        verify(syncPixels).fireBackupThisDeviceTapped()
    }

    @Test
    fun whenRecoverYourSyncedDataThenRecoverSyncDataTappedPixelFired() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)

        testee.onRecoverYourSyncedData()

        verify(syncPixels).fireRecoverSyncDataTapped()
    }

    @Test
    fun whenSyncWithAnotherDeviceThenEmitCommandSyncWithAnotherDevice() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        testee.onSyncWithAnotherDevice()

        testee.commands().test {
            awaitItem().assertCommandType(Command.SyncWithAnotherDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncWithAnotherDeviceWithoutDeviceAuthenticationThenNoCommandSent() = runViewModelTest {
        givenUserHasDeviceAuthentication(false)

        testee.commands().test {
            testee.onSyncWithAnotherDevice()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncWithAnotherDeviceAndCanRestoreThenShowPreviousSessionReady() = runViewModelTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        testee.onSyncWithAnotherDevice()

        testee.commands().test {
            val command = awaitItem()
            command.assertCommandType(ShowPreviousSessionReady::class)
            assertEquals(SyncEntryPoint.ADD_DEVICE, (command as ShowPreviousSessionReady).syncEntryPoint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScanAnotherDeviceQRCodeThenEmitCommandAddAnotherDevice() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        testee.onAddAnotherDevice()

        testee.commands().test {
            awaitItem().assertCommandType(Command.AddAnotherDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScanAnotherDeviceQRCodeWithoutDeviceAuthenticationThenNoCommandSent() = runViewModelTest {
        givenUserHasDeviceAuthentication(false)

        testee.commands().test {
            testee.onAddAnotherDevice()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedFromPromotionAndNotSignedInThenLaunchCreateAccountFlow() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.commands().test {
            testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false)
            awaitItem().assertCommandType(IntroCreateAccount::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenJoiningExistingAccountAndNotSignedInThenEmitCommandSyncWithAnotherDevice() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.commands().test {
            testee.processSourceDeepLink(launchSource = "promotion_ai_chat", joinExistingAccount = true, offerDesktopApp = false)
            awaitItem().assertCommandType(Command.SyncWithAnotherDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenJoiningExistingAccountAndCreateAccountDisabledThenEmitCommandSyncWithAnotherDevice() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(true)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(false)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.commands().test {
            testee.processSourceDeepLink(launchSource = "promotion_ai_chat", joinExistingAccount = true, offerDesktopApp = false)
            awaitItem().assertCommandType(Command.SyncWithAnotherDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenJoiningExistingAccountAndSetupFlowsDisabledThenContentIsShown() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(false)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.commands().test {
            testee.processSourceDeepLink(launchSource = "promotion_ai_chat", joinExistingAccount = true, offerDesktopApp = false)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState().test {
            assertFalse(expectMostRecentItem().isContentHidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedFromPromotionAndNotSignedInThenBackupThisDeviceTappedPixelNotFired() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false)

        verify(syncPixels, never()).fireBackupThisDeviceTapped()
    }

    @Test
    fun whenLaunchedFromPromotionAndCreateAccountDisabledThenNoCommandSent() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(true)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(false)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.commands().test {
            testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedFromPromotionAndSetupFlowsDisabledThenContentIsShown() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(false)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false)

        testee.viewState().test {
            assertFalse(expectMostRecentItem().isContentHidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedFromPromotionAndSignedInThenEmitCommandAddAnotherDevice() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.commands().test {
            testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false)
            awaitItem().assertCommandType(Command.AddAnotherDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAskedToSyncWithDesktopAndSignedInThenEmitCommandAskSyncWithDesktop() = runViewModelTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.commands().test {
            testee.processSourceDeepLink(
                launchSource = "promotion_bookmark_multi_device_added_dialog",
                joinExistingAccount = false,
                offerDesktopApp = true,
            )
            awaitItem().assertCommandType(Command.AskSyncWithDesktop::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAskedToSyncWithDesktopAndNotSignedInThenEmitCommandIntroCreateAccount() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.commands().test {
            testee.processSourceDeepLink(
                launchSource = "promotion_bookmark_multi_device_added_dialog",
                joinExistingAccount = false,
                offerDesktopApp = true,
            )
            awaitItem().assertCommandType(IntroCreateAccount::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedFromBookmarkAddedMultiDevicePromotionAndSignedInThenEmitCommandAddAnotherDevice() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.commands().test {
            testee.processSourceDeepLink(
                launchSource = "promotion_bookmark_multi_device_added_dialog",
                joinExistingAccount = false,
                offerDesktopApp = false,
            )
            awaitItem().assertCommandType(Command.AddAnotherDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncWithDesktopSelectedThenEmitCommandAddAnotherDevice() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)

        testee.commands().test {
            testee.onSyncWithDesktopSelected()
            awaitItem().assertCommandType(Command.AddAnotherDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoDesktopAppSelectedThenEmitCommandLaunchSyncGetOnOtherPlatforms() = runViewModelTest {
        testee.onNoDesktopAppSelected()
        testee.commands().test {
            assertEquals("promotion_bookmark_added_dialog", (awaitItem() as LaunchSyncGetOnOtherPlatforms).source.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedFromPromotionAndNotSignedInThenFirstViewStateHidesContent() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false)

        testee.viewState().test {
            assertTrue(awaitItem().isContentHidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedFromPromotionAndScreenExitsThenContentIsShown() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false)
        testee.onScreenExit()

        testee.viewState().test {
            assertFalse(expectMostRecentItem().isContentHidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCachedAsSignedInThenFlowStartShowsContent() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.cachedIsSignedIn()).thenReturn(true)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        assertFalse(testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false))
    }

    @Test
    fun whenCachedAsSignedOutThenFlowStartHidesContent() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.cachedIsSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        assertTrue(testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false))
    }

    @Test
    fun whenSignInStateNotCachedThenFlowStartHidesContent() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.cachedIsSignedIn()).thenReturn(null)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        assertTrue(testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false))
    }

    @Test
    fun whenJoiningExistingAccountThenFlowStartShowsContent() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.cachedIsSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        assertFalse(testee.processSourceDeepLink(launchSource = "promotion_ai_chat", joinExistingAccount = true, offerDesktopApp = false))
    }

    @Test
    fun whenCachedAsSignedInThenFirstViewStateShowsContent() = runViewModelTest {
        whenever(syncAccountRepository.cachedIsSignedIn()).thenReturn(true)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.processSourceDeepLink(launchSource = "promotion_bookmarks_multi_device", joinExistingAccount = false, offerDesktopApp = false)

        testee.viewState().test {
            assertFalse(awaitItem().isContentHidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOfferingDesktopAppAndSignedInThenContentIsShown() = runViewModelTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.processSourceDeepLink(
            launchSource = "promotion_bookmark_multi_device_added_dialog",
            joinExistingAccount = false,
            offerDesktopApp = true,
        )

        testee.viewState().test {
            assertFalse(expectMostRecentItem().isContentHidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenJoiningExistingAccountThenContentIsShownForAuthentication() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        givenSetupFlowsAllowed()
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)

        testee.processSourceDeepLink(launchSource = "promotion_ai_chat", joinExistingAccount = true, offerDesktopApp = false)

        testee.viewState().test {
            assertFalse(expectMostRecentItem().isContentHidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOpenedWithoutDeepLinkThenContentIsShown() = runViewModelTest {
        testee.viewState().test {
            assertFalse(expectMostRecentItem().isContentHidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLaunchedFromPromotionAndSignedInThenContentIsShownForAuthentication() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.processSourceDeepLink(launchSource = "promotion_passwords", joinExistingAccount = false, offerDesktopApp = false)

        testee.viewState().test {
            assertFalse(expectMostRecentItem().isContentHidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceThenLaunchCreateAccountFlow() = runViewModelTest {
        testee.commands().test {
            testee.onSyncThisDevice(launchSource = "other")
            awaitItem().assertCommandType(IntroCreateAccount::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceThenDoesNotAuthenticate() = runViewModelTest {
        testee.commands().test {
            testee.onSyncThisDevice(launchSource = "other")
            awaitItem()
            assertTrue(deviceAuthenticator.requests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceAndCanRestoreThenShowPreviousSessionReady() = runViewModelTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        testee.commands().test {
            testee.onSyncThisDevice(launchSource = "other")
            val command = awaitItem()
            command.assertCommandType(ShowPreviousSessionReady::class)
            assertEquals(SyncEntryPoint.SYNC_NEW_ACCOUNT, (command as ShowPreviousSessionReady).syncEntryPoint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceThenOnFlowStartedCalled() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        testee.commands().test {
            testee.onSyncThisDevice(launchSource = "other")
            awaitItem()
            verify(syncSetupWideEvent).onFlowStarted(launchSource = "other")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceWithSourceThenOnFlowStartedCalledWithSource() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        testee.commands().test {
            testee.onSyncThisDevice(launchSource = "promotion_passwords")
            awaitItem()
            verify(syncSetupWideEvent).onFlowStarted(launchSource = "promotion_passwords")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAddAnotherDeviceWithoutDeviceAuthThenSetupWideEventNotTouched() = runViewModelTest {
        givenUserHasDeviceAuthentication(false)

        testee.onAddAnotherDevice()

        verifyNoInteractions(syncSetupWideEvent)
    }

    @Test
    fun whenSyncWithAnotherDeviceAndCanRestoreThenShowPreviousSessionReadyWithoutAuthenticating() = runViewModelTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)

        testee.commands().test {
            testee.onSyncWithAnotherDevice()
            assertEquals(ShowPreviousSessionReady(SyncEntryPoint.ADD_DEVICE), awaitItem())
            assertTrue(deviceAuthenticator.requests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceAndCanRestoreThenShowPreviousSessionReadyWithoutAuthenticating() = runViewModelTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)

        testee.commands().test {
            testee.onSyncThisDevice(launchSource = "other")
            assertEquals(ShowPreviousSessionReady(SyncEntryPoint.SYNC_NEW_ACCOUNT), awaitItem())
            assertTrue(deviceAuthenticator.requests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRecoverSyncedDataAndCanRestoreThenShowPreviousSessionReadyWithoutAuthenticating() = runViewModelTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)

        testee.commands().test {
            testee.onRecoverYourSyncedData()
            assertEquals(ShowPreviousSessionReady(SyncEntryPoint.RECOVER_SYNCED_DATA), awaitItem())
            assertTrue(deviceAuthenticator.requests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncWithAnotherDeviceVerificationDismissedThenSetupWideEventNotTouched() = runViewModelTest {
        deviceAuthenticator.response = Response.Cancelled.VerificationDismissed

        testee.onSyncWithAnotherDevice()

        verifyNoInteractions(syncSetupWideEvent)
    }

    @Test
    fun whenAddAnotherDeviceVerificationFailsThenShowError() = runViewModelTest {
        deviceAuthenticator.response = Response.Failed("reason")

        testee.commands().test {
            testee.onAddAnotherDevice()
            val command = awaitItem()
            command.assertCommandType(Command.ShowError::class)
            assertEquals("reason", (command as Command.ShowError).reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenViewModelCreatedThenAuthPromptsExposeAuthenticatorPrompts() = runViewModelTest {
        assertEquals(deviceAuthenticator.currentPrompt, testee.authPrompts)
    }

    @Test
    fun whenConnectionCancelledThenOnFlowCancelledCalled() = runViewModelTest {
        testee.onConnectionCancelled()

        verify(syncSetupWideEvent).onFlowCancelled()
    }

    @Test
    fun whenSyncThisDeviceThenThisDeviceSyncInProgress() = runViewModelTest {
        testee.viewState().test {
            testee.onSyncThisDevice(launchSource = "other")
            assertTrue(expectMostRecentItem().isThisDeviceSyncing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceCanceledThenThisDeviceSyncIdle() = runViewModelTest {
        testee.viewState().test {
            testee.onSyncThisDevice(launchSource = "other")
            assertTrue(expectMostRecentItem().isThisDeviceSyncing)

            testee.onSyncThisDeviceCanceled()
            assertFalse(expectMostRecentItem().isThisDeviceSyncing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenContinueSetupAfterSkipRestoreThenLaunchOriginalFlowWithoutAuth() = runViewModelTest {
        whenever(syncAutoRestoreManager.clearAutoRestoreData()).thenReturn(Result.Success(Unit))

        testee.commands().test {
            testee.onContinueSetupAfterSkipRestore(SyncEntryPoint.SYNC_NEW_ACCOUNT)
            assertEquals(LaunchOriginalFlow(SyncEntryPoint.SYNC_NEW_ACCOUNT, isAuthRequired = false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSkipRestoreClearFailsThenThisDeviceSyncIdle() = runViewModelTest {
        whenever(syncAutoRestoreManager.clearAutoRestoreData()).thenReturn(Result.Error(reason = "error"))

        testee.viewState().test {
            testee.onSyncThisDevice(launchSource = "other")
            assertTrue(expectMostRecentItem().isThisDeviceSyncing)

            testee.onContinueSetupAfterSkipRestore(SyncEntryPoint.SYNC_NEW_ACCOUNT)
            assertFalse(expectMostRecentItem().isThisDeviceSyncing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRecoverDataThenRecoverDataCommandSent() = runViewModelTest {
        testee.onRecoverYourSyncedData()

        testee.commands().test {
            awaitItem().assertCommandType(IntroRecoverSyncData::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRecoverDataThenDoesNotAuthenticate() = runViewModelTest {
        testee.commands().test {
            testee.onRecoverYourSyncedData()
            awaitItem()
            assertTrue(deviceAuthenticator.requests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRecoverDataThenManualRecoveryShownPixelFired() = runViewModelTest {
        testee.commands().test {
            testee.onRecoverYourSyncedData()
            awaitItem()
            verify(syncPixels).fireAutoRestoreSettingsManualRecoveryShown()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRecoverSyncedDataAndCanRestoreThenShowPreviousSessionReady() = runViewModelTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        testee.onRecoverYourSyncedData()

        testee.commands().test {
            val command = awaitItem()
            command.assertCommandType(ShowPreviousSessionReady::class)
            assertEquals(SyncEntryPoint.RECOVER_SYNCED_DATA, (command as ShowPreviousSessionReady).syncEntryPoint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffSyncConfirmedThenLogoutLocalDevice() = runViewModelTest {
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onTurnOffSyncConfirmed(connectedDevice)

        verify(syncAccountRepository).logout(deviceId)
    }

    @Test
    fun whenTurnOffSyncConfirmedThenPixelFired() = runViewModelTest {
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onTurnOffSyncConfirmed(connectedDevice)

        verify(syncPixels).fireUserConfirmedToTurnOffSync()
    }

    @Test
    fun whenTurnOffSyncConfirmedFailsThenErrorShown() = runViewModelTest {
        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.commands().test {
            testee.onTurnOffSyncConfirmed(connectedDevice)

            awaitItem().assertCommandType(Command.ShowError::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLogoutSuccessThenUpdateViewState() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.viewState().test {
            var viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            testee.onTurnOffSyncConfirmed(connectedDevice)
            viewState = awaitItem()
            assertFalse(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLogoutErrorThenUpdateViewState() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.onTurnOffSyncConfirmed(connectedDevice)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffSyncCancelledThenDeviceSyncViewStateIsEnabled() = runViewModelTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            testee.onTurnOffSyncCancelled()
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountClickedAndOnlyThisDeviceThenAskTurnOffSync() = runViewModelTest {
        givenAuthenticatedUser()
        givenUserHasDeviceAuthentication(true)

        testee.viewState().test {
            testee.commands().test {
                testee.onDeleteAccountClicked()
                assertEquals(AskTurnOffSync(thisDevice = connectedDevice, allDevices = listOf(connectedDevice)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountClickedAndMultipleDevicesThenAskTurnOffSync() = runViewModelTest {
        givenAuthenticatedUser()
        givenUserHasDeviceAuthentication(true)
        val otherDevice = connectedDevice.copy(thisDevice = false, deviceId = "otherDeviceId")
        whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(listOf(connectedDevice, otherDevice)))

        testee.viewState().test {
            testee.commands().test {
                testee.onDeleteAccountClicked()
                assertEquals(AskTurnOffSync(thisDevice = connectedDevice, allDevices = listOf(connectedDevice, otherDevice)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffAndDeleteServerDataClickedThenAskDeleteServerData() = runViewModelTest {
        testee.commands().test {
            testee.onTurnOffAndDeleteServerDataClicked()
            awaitItem().assertCommandType(Command.AskDeleteServerData::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffThisDeviceClickedThenAskTurnOffThisDevice() = runViewModelTest {
        testee.commands().test {
            testee.onTurnOffThisDeviceClicked(connectedDevice)
            assertEquals(Command.AskTurnOffThisDevice(connectedDevice), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountClickedWithoutDeviceAuthenticationThenNoCommandSent() = runViewModelTest {
        givenUserHasDeviceAuthentication(false)

        testee.commands().test {
            testee.onDeleteAccountClicked()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteServerDataConfirmedAndSuccessThenUpdateViewState() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.deleteAccount()).thenReturn(Result.Success(true))

        testee.viewState().test {
            var viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            testee.onDeleteServerDataConfirmed()
            viewState = awaitItem()
            assertFalse(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteServerDataConfirmedAndErrorThenUpdateViewState() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.deleteAccount()).thenReturn(Result.Error(reason = "error"))

        testee.onDeleteServerDataConfirmed()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteServerDataConfirmedThenDeleteAccount() = runViewModelTest {
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))
        whenever(syncAccountRepository.deleteAccount()).thenReturn(Result.Success(true))

        testee.onDeleteServerDataConfirmed()

        verify(syncPixels).fireDeleteServerDataConfirmationConfirmed()
        verify(syncAccountRepository).deleteAccount()
    }

    @Test
    fun whenDeleteServerDataCancelledThenDeviceSyncViewStateIsEnabled() = runViewModelTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            testee.onDeleteServerDataCancelled()
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
        verify(syncPixels).fireDeleteServerDataConfirmationDismissed()
    }

    @Test
    fun whenOnEditDeviceClickedWithDeviceAuthenticationThenAskEditDevice() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)

        testee.onEditDeviceClicked(connectedDevice)

        testee.commands().test {
            val command = awaitItem()
            command.assertCommandType(AskEditDevice::class)
            assertEquals(connectedDevice, (command as AskEditDevice).device)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditDeviceClickedWithoutDeviceAuthenticationThenNoCommandSent() = runViewModelTest {
        givenUserHasDeviceAuthentication(false)

        testee.commands().test {
            testee.onEditDeviceClicked(connectedDevice)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnRemoveDeviceConfirmedThenRemoveDevice() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onRemoveDeviceConfirmed(connectedDevice)

        verify(syncAccountRepository).logout(deviceId)
    }

    @Test
    fun whenOnRemoveDeviceSucceedsThenDeviceIsRemovedWithoutRefetch() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onRemoveDeviceConfirmed(connectedDevice)

        verify(syncAccountRepository, never()).getConnectedDevices()
    }

    @Test
    fun whenOnRemoveDeviceSucceedsThenReturnUpdateDevices() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.viewState().test {
            var awaitItem = expectMostRecentItem()
            assertEquals(1, awaitItem.syncedDevices.size)
            testee.onRemoveDeviceConfirmed(connectedDevice)
            awaitItem = expectMostRecentItem()
            assertEquals(0, awaitItem.syncedDevices.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenStaleDeviceFetchCompletesAfterRemovalThenRemovedDeviceDoesNotReappear() = runViewModelTest {
        givenAuthenticatedUser()

        val otherDevice = connectedDevice.copy(thisDevice = false, deviceId = "otherDeviceId")
        whenever(syncAccountRepository.logout(otherDevice.deviceId)).thenReturn(Result.Success(true))
        var fetchCount = 0
        whenever(syncAccountRepository.getConnectedDevices()).thenAnswer {
            fetchCount++
            if (fetchCount == 2) {
                // this fetch read the server before the removal: the user confirms the removal
                // while it is in flight, so its stale result lands afterwards
                testee.onRemoveDeviceConfirmed(otherDevice)
            }
            Result.Success(listOf(connectedDevice, otherDevice))
        }

        testee.viewState().test {
            assertEquals(2, expectMostRecentItem().syncedDevices.size)

            testee.onDevicesUpdated()

            val devices = expectMostRecentItem().syncedDevices.filterIsInstance<SyncedDevice>()
            assertEquals(listOf(deviceId), devices.map { it.device.deviceId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnRemoveDeviceFailsThenDeviceRemainsInList() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.viewState().test {
            testee.onRemoveDeviceConfirmed(connectedDevice)
            val awaitItem = expectMostRecentItem()
            assertEquals(1, awaitItem.syncedDevices.size)
            assertFalse((awaitItem.syncedDevices.first() as SyncedDevice).loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDeviceEditedThenUpdateDevice() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.renameDevice(any())).thenReturn(Result.Success(true))

        testee.viewState().test {
            var awaitItem = expectMostRecentItem()
            val newDevice = connectedDevice.copy(deviceName = "newDevice")
            whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(listOf(newDevice)))
            testee.onDeviceEdited(newDevice)
            awaitItem = expectMostRecentItem()
            assertNotNull(awaitItem.syncedDevices.filterIsInstance<SyncedDevice>().first { it.device.deviceName == newDevice.deviceName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDeviceEditedFailsThenDeviceRemainsInList() = runViewModelTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.renameDevice(any())).thenReturn(Result.Error(reason = "error"))

        testee.viewState().test {
            testee.onDeviceEdited(connectedDevice.copy(deviceName = "newDevice"))
            val awaitItem = expectMostRecentItem()
            assertEquals(1, awaitItem.syncedDevices.size)
            assertFalse((awaitItem.syncedDevices.first() as SyncedDevice).loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnSaveRecoveryCodeThenEmitCheckIfUserHasPermissionCommand() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        testee.commands().test {
            testee.onSaveRecoveryCodeClicked()
            val command = awaitItem()
            assertTrue(command is CheckIfUserHasStoragePermission)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnSaveRecoveryCodeWithoutDeviceAuthenticationThenNoCommandSent() = runViewModelTest {
        givenUserHasDeviceAuthentication(false)

        testee.commands().test {
            testee.onSaveRecoveryCodeClicked()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenGenerateRecoveryCodeThenGenerateFileAndEmitSuccessCommand() = runViewModelTest {
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(recoveryPDF.generateAndStoreRecoveryCodePDF(any(), eq(authCodeToUse.rawCode))).thenReturn(TestSyncFixtures.pdfFile())

        testee.commands().test {
            testee.generateRecoveryCode(mock())
            val command = awaitItem()
            assertTrue(command is RecoveryCodePDFSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnCopyRecoveryCodeWithoutDeviceAuthenticationThenCodeIsNotCopied() = runViewModelTest {
        givenUserHasDeviceAuthentication(false)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))

        testee.onCopyRecoveryCodeClicked()

        verify(clipboardInteractor, never()).copyToClipboard(any(), any())
    }

    @Test
    fun whenCopyRecoveryCodeAuthenticatedThenCodeIsCopiedToClipboard() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))

        testee.onCopyRecoveryCodeClicked()

        verify(clipboardInteractor).copyToClipboard(authCodeToUse.rawCode, isSensitive = true)
    }

    @Test
    fun whenCopyRecoveryCodeAuthenticatedAndSystemShowsNoNotificationThenEmitShowMessageCommand() = runViewModelTest {
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(clipboardInteractor.copyToClipboard(any(), any())).thenReturn(false)

        testee.commands().test {
            testee.onCopyRecoveryCodeClicked()
            val command = awaitItem()
            assertTrue(command is ShowMessage)
            assertEquals(R.string.sync_code_copied_message, (command as ShowMessage).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCopyRecoveryCodeAuthenticatedAndSystemShowsNotificationThenNoMessageShown() = runViewModelTest {
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(clipboardInteractor.copyToClipboard(any(), any())).thenReturn(true)

        testee.commands().test {
            testee.onCopyRecoveryCodeClicked()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCopyRecoveryCodeAuthenticatedAndGettingRecoveryCodeFailsThenEmitShowErrorCommand() = runViewModelTest {
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Error(reason = "error"))

        testee.commands().test {
            testee.onCopyRecoveryCodeClicked()
            awaitItem().assertCommandType(Command.ShowError::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDeviceConnectedThenFetchRemoteDevices() = runViewModelTest {
        givenAuthenticatedUser()

        val connectedDevices = listOf(connectedDevice, connectedDevice)
        whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(connectedDevices))

        testee.viewState().test {
            testee.onSyncThisDevice(launchSource = "other")
            assertTrue(expectMostRecentItem().isThisDeviceSyncing)

            testee.onDeviceConnected()

            val initialState = expectMostRecentItem()
            assertEquals(connectedDevices.size, initialState.syncedDevices.size)
            assertFalse(initialState.isThisDeviceSyncing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDevicesUpdatedThenViewStateReflectsRefreshedDevices() = runViewModelTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()

            val updatedDevice = connectedDevice.copy(deviceName = "updatedDevice")
            whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Success(listOf(updatedDevice)))
            testee.onDevicesUpdated()
            val refreshedItem = expectMostRecentItem()
            assertNotNull(refreshedItem.syncedDevices.filterIsInstance<SyncedDevice>().first { it.device.deviceName == updatedDevice.deviceName })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncStateIsDisabledThenViewStateChanges() = runViewModelTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val initialState = expectMostRecentItem()
            assertEquals(initialState.showAccount, true)
            stateFlow.value = OFF
            val updatedState = awaitItem()
            assertEquals(updatedState.showAccount, false)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncStateIsEnabledThenViewStateChanges() = runViewModelTest {
        givenAuthenticatedUser()
        stateFlow.value = SyncState.OFF

        testee.viewState().test {
            val initialState = expectMostRecentItem()
            assertEquals(initialState.showAccount, false)
            stateFlow.value = READY
            val updatedState = awaitItem()
            assertEquals(updatedState.showAccount, true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncStateIsInProgressEnabledThenViewStateDoesNotChange() = runViewModelTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val initialState = expectMostRecentItem()
            assertEquals(initialState.showAccount, true)
            stateFlow.value = IN_PROGRESS
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedAndSetupFlowsDisabledThenAllSetupFlowsDisabledViewState() = runViewModelTest {
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(false)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.disabledSetupFlows.contains(SignInFlow))
            assertTrue(viewState.disabledSetupFlows.contains(CreateAccountFlow))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedAndCreateAccountDisabledThenOnlySignInFlowDisabledViewState() = runViewModelTest {
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(true)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.disabledSetupFlows.contains(SignInFlow))
            assertTrue(viewState.disabledSetupFlows.contains(CreateAccountFlow))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSetupFlowsDisabledThenAllSetupFlowsDisabledViewState() = runViewModelTest {
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(false)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(true)

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.disabledSetupFlows.contains(SignInFlow))
            assertTrue(viewState.disabledSetupFlows.contains(CreateAccountFlow))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCreateAccountDisabledThenOnlySignInFlowDisabledViewState() = runViewModelTest {
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(true)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(false)

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.disabledSetupFlows.contains(SignInFlow))
            assertTrue(viewState.disabledSetupFlows.contains(CreateAccountFlow))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncNotSupportedThenEmitCommandShowDeviceUnsupported() = runViewModelTest {
        whenever(syncAccountRepository.isSyncSupported()).thenReturn(false)

        testee.commands().test {
            awaitItem().assertCommandType(Command.ShowDeviceUnsupported::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSignedInAndAuditLogEnabledThenShowDeviceActivity() = runViewModelTest {
        givenAuthenticatedUser()
        fakeSyncFeature.loginAuditLog().setRawStoredState(State(true))

        testee.viewState().test {
            assertTrue(expectMostRecentItem().showDeviceActivity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSignedInAndAuditLogDisabledThenHideDeviceActivity() = runViewModelTest {
        givenAuthenticatedUser()
        fakeSyncFeature.loginAuditLog().setRawStoredState(State(false))

        testee.viewState().test {
            assertFalse(expectMostRecentItem().showDeviceActivity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSignedOutAndAuditLogEnabledThenHideDeviceActivity() = runViewModelTest {
        fakeSyncFeature.loginAuditLog().setRawStoredState(State(true))

        testee.viewState().test {
            assertFalse(expectMostRecentItem().showDeviceActivity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeviceActivityClickedThenLaunchDeviceActivity() = runViewModelTest {
        testee.onDeviceActivityClicked()

        testee.commands().test {
            assertEquals(LaunchDeviceActivity, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSignedInWithPendingLoginsAndNotificationsEnabledThenShowNewDeviceBanner() = runViewModelTest {
        givenAuthenticatedUser()
        givenLoginNotificationsEnabled()
        auditLogStateStore.state.value = AuditLogCheckState(pendingLogins = listOf(pendingLogin(1, "Laptop")))

        testee.viewState().test {
            assertEquals(listOf("Laptop"), expectMostRecentItem().newDeviceBanner?.deviceNames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoPendingLoginsThenHideNewDeviceBanner() = runViewModelTest {
        givenAuthenticatedUser()
        givenLoginNotificationsEnabled()

        testee.viewState().test {
            assertNull(expectMostRecentItem().newDeviceBanner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLoginNotificationFlagOffThenHideNewDeviceBanner() = runViewModelTest {
        givenAuthenticatedUser()
        fakeSyncFeature.loginAuditLog().setRawStoredState(State(true))
        fakeSyncFeature.loginNotification().setRawStoredState(State(false))
        auditLogStateStore.state.value = AuditLogCheckState(pendingLogins = listOf(pendingLogin(1, "Laptop")))

        testee.viewState().test {
            assertNull(expectMostRecentItem().newDeviceBanner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAuditLogFlagOffThenHideNewDeviceBanner() = runViewModelTest {
        givenAuthenticatedUser()
        fakeSyncFeature.loginAuditLog().setRawStoredState(State(false))
        fakeSyncFeature.loginNotification().setRawStoredState(State(true))
        auditLogStateStore.state.value = AuditLogCheckState(pendingLogins = listOf(pendingLogin(1, "Laptop")))

        testee.viewState().test {
            assertNull(expectMostRecentItem().newDeviceBanner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSignedOutThenHideNewDeviceBanner() = runViewModelTest {
        givenLoginNotificationsEnabled()
        auditLogStateStore.state.value = AuditLogCheckState(pendingLogins = listOf(pendingLogin(1, "Laptop")))

        testee.viewState().test {
            assertNull(expectMostRecentItem().newDeviceBanner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignsInWhileOpenThenBannerShowsNewLogins() = runViewModelTest {
        givenLoginNotificationsEnabled()
        val syncStates = MutableStateFlow(SyncState.OFF)
        whenever(syncStateMonitor.syncState()).thenReturn(syncStates)

        testee.viewState().test {
            assertNull(expectMostRecentItem().newDeviceBanner)

            givenAuthenticatedUser()
            syncStates.value = SyncState.READY
            auditLogStateStore.state.value = AuditLogCheckState(pendingLogins = listOf(pendingLogin(1, "Laptop")))

            assertEquals(listOf("Laptop"), expectMostRecentItem().newDeviceBanner?.deviceNames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPendingLoginsAddedWhileOpenThenBannerUpdates() = runViewModelTest {
        givenAuthenticatedUser()
        givenLoginNotificationsEnabled()

        testee.viewState().test {
            assertNull(expectMostRecentItem().newDeviceBanner)

            auditLogStateStore.state.value = AuditLogCheckState(pendingLogins = listOf(pendingLogin(1, "Laptop")))

            assertEquals(listOf("Laptop"), expectMostRecentItem().newDeviceBanner?.deviceNames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPendingLoginsClearedElsewhereThenBannerHides() = runViewModelTest {
        givenAuthenticatedUser()
        givenLoginNotificationsEnabled()
        auditLogStateStore.state.value = AuditLogCheckState(pendingLogins = listOf(pendingLogin(1, "Laptop")))

        testee.viewState().test {
            assertNotNull(expectMostRecentItem().newDeviceBanner)

            auditLogStateStore.state.value = AuditLogCheckState()

            assertNull(expectMostRecentItem().newDeviceBanner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNewDeviceBannerReviewClickedThenLaunchDeviceActivity() = runViewModelTest {
        testee.onNewDeviceBannerReviewClicked()

        testee.commands().test {
            assertEquals(LaunchDeviceActivity, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNewDeviceBannerClosedThenOnlyShownLoginsAreCleared() = runViewModelTest {
        givenAuthenticatedUser()
        givenLoginNotificationsEnabled()
        auditLogStateStore.state.value = AuditLogCheckState(cursor = "after-1", pendingLogins = listOf(pendingLogin(1, "Laptop")))

        testee.viewState().test {
            assertNotNull(expectMostRecentItem().newDeviceBanner)
            auditLogStateStore.foundDuringClose = pendingLogin(2, "Phone")

            testee.onNewDeviceBannerCloseClicked()

            assertEquals(listOf(pendingLogin(2, "Phone")), auditLogStateStore.state.value.pendingLogins)
            assertEquals("after-1", auditLogStateStore.state.value.cursor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNewDeviceBannerClosedWithNoBannerThenStoreUntouched() = runViewModelTest {
        testee.onNewDeviceBannerCloseClicked()

        assertEquals(0, auditLogStateStore.updateCount)
    }

    @Test
    fun whenLearnMoreClickedThenEmitLaunchLearnMoreCommand() = runViewModelTest {
        testee.onLearnMoreClicked()

        testee.commands().test {
            awaitItem().assertCommandType(LaunchLearnMore::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLearnMoreClickedThenCommandContainsCorrectUrl() = runViewModelTest {
        testee.onLearnMoreClicked()

        testee.commands().test {
            val command = awaitItem() as LaunchLearnMore
            assertEquals(
                "https://duckduckgo.com/duckduckgo-help-pages/sync-and-backup/recovery-codes-and-troubleshooting#data-expiration",
                command.url,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenClickedToGetAppOnOtherPlatformsClickedInEnabledStateThenEmitCommand() = runViewModelTest {
        testee.onGetOnOtherPlatformsClickedWhenSyncEnabled()
        testee.commands().test {
            awaitItem().also {
                assertEquals("activated", (it as LaunchSyncGetOnOtherPlatforms).source.value)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenClickedToGetAppOnOtherPlatformsClickedInDisabledStateThenEmitCommand() = runViewModelTest {
        testee.onGetOnOtherPlatformsClickedWhenSyncDisabled()
        testee.commands().test {
            awaitItem().also {
                assertEquals("not_activated", (it as LaunchSyncGetOnOtherPlatforms).source.value)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDesktopBrowserFeatureEnabledAndSignedOutThenViewStateShowsNewDesktopBrowserSetting() = runViewModelTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(true))

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.newDesktopBrowserSettingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDesktopBrowserFeatureDisabledAndSignedOutThenViewStateDoesNotShowNewDesktopBrowserSetting() = runViewModelTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(false))

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.newDesktopBrowserSettingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDesktopBrowserFeatureEnabledAndSignedInThenViewStateShowsNewDesktopBrowserSetting() = runViewModelTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(true))
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.newDesktopBrowserSettingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDesktopBrowserFeatureDisabledAndSignedInThenViewStateDoesNotShowNewDesktopBrowserSetting() = runViewModelTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(false))
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.newDesktopBrowserSettingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedOutThenAutoRestoreToggleIsHidden() = runViewModelTest {
        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.showAutoRestoreToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreAvailableThenViewStateShowsToggle() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAutoRestoreToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreNotAvailableThenViewStateHidesToggle() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.showAutoRestoreToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreEnabledThenViewStateReflectsEnabled() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.autoRestoreEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreDisabledThenViewStateReflectsDisabled() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.autoRestoreEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreToggleChangedThenUpdatesViewStateOnly() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(false)
            val updatedState = awaitItem()
            assertFalse(updatedState.autoRestoreEnabled)
            verify(syncAutoRestoreManager, never()).saveAutoRestoreData(any(), anyOrNull())
            verify(syncAutoRestoreManager, never()).clearAutoRestoreData()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncStateReEmitsAfterToggleChangedThenPendingToggleStatePreserved() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(false)
            assertFalse(awaitItem().autoRestoreEnabled)

            // Sync state re-emits (e.g. a background sync completes) — toggle should not revert.
            // MutableStateFlow only emits when value changes, so if toggle is correctly preserved
            // (state unchanged) we expect no new emission.
            stateFlow.value = IN_PROGRESS
            stateFlow.value = READY
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScreenExitsWithAutoRestoreEnabledThenSavesPayloadAndSetsPreference() = runViewModelTest {
        val authCode = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "rawCode")
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager).saveAutoRestoreData(eq("rawCode"), anyOrNull())
    }

    @Test
    fun whenScreenExitsWithAutoRestoreEnabledButGetRecoveryCodeFailsThenPreferenceNotWritten() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Error(reason = "error"))

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager, never()).saveAutoRestoreData(any(), anyOrNull())
    }

    @Test
    fun whenScreenExitsWithAutoRestoreDisabledThenClearsPayloadAndSetsPreference() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(false)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager).clearAutoRestoreData()
    }

    @Test
    fun whenScreenExitsTwiceWithAutoRestoreEnabledThenSavesPayloadOnlyOnce() = runViewModelTest {
        val authCode = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "rawCode")
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()
        testee.onScreenExit()

        verify(syncAutoRestoreManager, times(1)).saveAutoRestoreData(eq("rawCode"), anyOrNull())
    }

    @Test
    fun whenScreenExitsTwiceWithAutoRestoreDisabledThenClearsPayloadOnlyOnce() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(false)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()
        testee.onScreenExit()

        verify(syncAutoRestoreManager, times(1)).clearAutoRestoreData()
    }

    @Test
    fun whenScreenExitsAndFeatureWasUnavailableThenNoStorageOperations() = runViewModelTest {
        // Feature unavailable at load time — autoRestoreAvailable captured as false
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(false)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager, never()).saveAutoRestoreData(any(), anyOrNull())
        verify(syncAutoRestoreManager, never()).clearAutoRestoreData()
    }

    @Test
    fun whenScreenExitsWithNoNetChangeToToggleThenNoStorageOperations() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            testee.onAutoRestoreToggleChanged(false)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager, never()).saveAutoRestoreData(any(), anyOrNull())
        verify(syncAutoRestoreManager, never()).clearAutoRestoreData()
    }

    @Test
    fun whenSignedOutThenSignedBackInThenAutoRestoreToggleIsVisible() = runViewModelTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            val signedInState = expectMostRecentItem()
            assertTrue(signedInState.showAutoRestoreToggle)

            stateFlow.value = OFF
            val signedOutState = awaitItem()
            assertFalse(signedOutState.showAutoRestoreToggle)

            stateFlow.value = READY
            val reSignedInState = awaitItem()
            assertTrue(reSignedInState.showAutoRestoreToggle)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestorePreferenceWrittenDuringSetupThenResubscribingToViewStateShowsCorrectToggleState() = runViewModelTest {
        // Simulate the race: account created (signed-in state fires) before the setup screen writes
        // the preference. The first viewState() collection reads 'false' from DataStore.
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            assertFalse(expectMostRecentItem().autoRestoreEnabled)
            cancelAndIgnoreRemainingEvents()
        }

        // Setup flow now writes 'true' to DataStore (user allowed auto-restore).
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)

        // SyncActivity returns to foreground — viewState() is re-subscribed.
        testee.viewState().test {
            assertTrue(expectMostRecentItem().autoRestoreEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessSetupDeepLinkWithV1ExchangeUrlThenAskSetupSyncDeepLinkIsSent() = runViewModelTest {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "code").asUrl()
        whenever(syncAccountRepository.parseSyncAuthCode(url)).thenReturn(SyncAuthCode.Exchange(mock()))

        testee.commands().test {
            testee.processSetupDeepLink(url)
            awaitItem().assertCommandType(Command.AskSetupSyncDeepLink::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessSetupDeepLinkWithV1ConnectUrlThenNoCommandIsSent() = runViewModelTest {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "code").asUrl()
        whenever(syncAccountRepository.parseSyncAuthCode(url)).thenReturn(SyncAuthCode.Unknown("code"))

        testee.commands().test {
            testee.processSetupDeepLink(url)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessSetupDeepLinkWithV2UrlAndDeviceAuthEnrolledThenDeepLinkIntoSetupIsSent() = runViewModelTest {
        givenUserHasDeviceAuthentication(true)
        val url = SyncBarcodeUrl(
            webSafeB64EncodedCode = "code",
            protocolVersion = SyncBarcodeUrl.ProtocolVersion.V2,
        ).asUrl()

        testee.commands().test {
            testee.processSetupDeepLink(url)
            awaitItem().assertCommandType(Command.DeepLinkIntoSetup::class)
            cancelAndIgnoreRemainingEvents()
        }

        verify(syncAccountRepository, never()).parseSyncAuthCode(any())
    }

    @Test
    fun whenProcessSetupDeepLinkWithV2UrlThenAuthenticateWithDeepLinkPrompt() = runViewModelTest {
        val url = SyncBarcodeUrl(
            webSafeB64EncodedCode = "code",
            protocolVersion = SyncBarcodeUrl.ProtocolVersion.V2,
        ).asUrl()

        testee.processSetupDeepLink(url)

        val request = deviceAuthenticator.requests.single()
        assertEquals(R.string.sync_simplified_deep_link_auth_prompt_title, request.verifyPromptTitle)
        assertEquals(R.string.sync_simplified_deep_link_auth_prompt_message, request.verifyPromptMessage)
    }

    @Test
    fun whenProcessSetupDeepLinkWithV2UrlAndDeviceAuthNotEnrolledThenNoCommandIsSent() = runViewModelTest {
        givenUserHasDeviceAuthentication(false)
        val url = SyncBarcodeUrl(
            webSafeB64EncodedCode = "code",
            protocolVersion = SyncBarcodeUrl.ProtocolVersion.V2,
        ).asUrl()

        testee.commands().test {
            testee.processSetupDeepLink(url)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessSetupDeepLinkWithMalformedUrlThenNoCommandIsSent() = runViewModelTest {
        testee.commands().test {
            testee.processSetupDeepLink("not-a-sync-url")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun Command.assertCommandType(expectedType: KClass<out Command>) {
        assertTrue(format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }

    private fun givenAuthenticatedUser() {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        whenever(syncStateMonitor.syncState()).thenReturn(stateFlow.asStateFlow())
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Success(listOf(connectedDevice)))
    }

    private fun givenLoginNotificationsEnabled() {
        fakeSyncFeature.loginAuditLog().setRawStoredState(State(true))
        fakeSyncFeature.loginNotification().setRawStoredState(State(true))
    }

    private fun pendingLogin(n: Int, name: String) = PendingLogin(
        entryId = "entry-$n",
        deviceId = "device-$n",
        deviceName = name,
        occurredAt = Instant.parse("2026-11-19T10:00:00Z").plusSeconds(n.toLong()),
    )

    private class FakeStateStore : AuditLogCheckStateStore {
        val state = MutableStateFlow(AuditLogCheckState())
        var updateCount = 0
            private set

        var foundDuringClose: PendingLogin? = null

        override suspend fun read(): AuditLogCheckState = state.value

        override suspend fun update(transform: (AuditLogCheckState) -> AuditLogCheckState?) {
            updateCount++
            foundDuringClose?.let { found -> state.value = state.value.copy(pendingLogins = state.value.pendingLogins + found) }
            foundDuringClose = null
            transform(state.value)?.let { state.value = it }
        }

        override fun pendingLogins(): Flow<List<PendingLogin>> = state.map { it.pendingLogins }
    }

    private fun givenSetupFlowsAllowed() {
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(true)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(true)
    }

    private fun givenUserHasDeviceAuthentication(hasDeviceAuthentication: Boolean) {
        if (hasDeviceAuthentication) {
            deviceAuthenticator.events = listOf(Event.VerificationShown)
            deviceAuthenticator.response = Response.Allowed.UserAuthenticated
        } else {
            deviceAuthenticator.events = listOf(Event.EnrollmentNeeded, Event.EnrollmentShown)
            deviceAuthenticator.response = Response.Cancelled.EnrollmentClosed
        }
    }
}
