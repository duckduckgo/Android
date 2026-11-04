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

package com.duckduckgo.pir.impl.dashboard

import app.cash.turbine.test
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.PirUserUtils
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewViewModel.Command
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewViewModel.Command.LaunchSubscriptionPurchase
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreemiumDashboardScreens
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreemiumDashboardScreens.FromEntryPoint
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreemiumDashboardScreens.FromRemoteMessage
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.purchase.PirPurchaseRoute
import com.duckduckgo.pir.impl.pixels.PirInteractionReporter
import com.duckduckgo.pir.impl.pixels.PirNotificationPromptType
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult
import com.duckduckgo.pir.impl.store.PirDataStore
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PirDashboardWebViewViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pirPixelSender: PirPixelSender = mock()
    private val pirInteractionReporter: PirInteractionReporter = mock()
    private val pirUserUtils: PirUserUtils = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val pirRepository: PirRepository = mock()
    private val subscriptions: Subscriptions = mock()
    private val pirFreemiumDataStore: PirFreemiumDataStore = mock()
    private val subscriptionStatus = MutableStateFlow(SubscriptionStatus.UNKNOWN)
    private val pirDataStore: PirDataStore = mock()

    private val route = PirPurchaseRoute.NativePurchaseFlow(origin = "funnel_freescan_android", featurePage = "pir")

    private lateinit var testee: PirDashboardWebViewViewModel

    @Before
    fun setUp() {
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(subscriptionStatus)
        testee = PirDashboardWebViewViewModel(
            pirPixelSender = pirPixelSender,
            pirInteractionReporter = pirInteractionReporter,
            pirUserUtils = pirUserUtils,
            appBuildConfig = appBuildConfig,
            pirRepository = pirRepository,
            subscriptions = subscriptions,
            pirFreemiumDataStore = pirFreemiumDataStore,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            pirDataStore = pirDataStore,
        )
    }

    @Test
    fun `test emits launch purchase command carrying the routes parameters`() = runTest {
        openDashboard(freemiumLaunch = null)

        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(
                PirPurchaseRoute.NativePurchaseFlow(origin = "funnel_freescan_android", featurePage = "pir"),
            )

            assertEquals(
                LaunchSubscriptionPurchase(origin = "funnel_freescan_android", featurePage = "pir"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test ignores a second purchase request while one is already outstanding`() = runTest {
        openDashboard(freemiumLaunch = null)
        advanceUntilIdle()

        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(route)
            assertEquals(LaunchSubscriptionPurchase(origin = "funnel_freescan_android", featurePage = "pir"), awaitItem())

            testee.onSubscriptionPurchaseRequested(route)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test allows a purchase request again after returning to the dashboard`() = runTest {
        openDashboard(freemiumLaunch = null)
        advanceUntilIdle()

        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(route)
            assertEquals(LaunchSubscriptionPurchase(origin = "funnel_freescan_android", featurePage = "pir"), awaitItem())

            testee.onStart(mock())
            testee.onSubscriptionPurchaseRequested(route)

            assertEquals(LaunchSubscriptionPurchase(origin = "funnel_freescan_android", featurePage = "pir"), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test does not reload on first subscription status emission`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test reloads when the user becomes signed in`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            subscriptionStatus.value = SubscriptionStatus.AUTO_RENEWABLE

            assertEquals(Command.ReloadWebView, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test does not reload when the status changes within signed in`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            subscriptionStatus.value = SubscriptionStatus.AUTO_RENEWABLE
            assertEquals(Command.ReloadWebView, awaitItem())

            subscriptionStatus.value = SubscriptionStatus.GRACE_PERIOD

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test reports the notification opened pixel when launched from the freemium scan complete notification`() {
        testee.onLaunched(isRestored = false, fromFreemiumScanCompleteNotification = true, freemiumLaunch = null)

        verify(pirPixelSender).reportFreemiumScanCompleteNotificationOpened()
    }

    @Test
    fun `test does not report the notification opened pixel for a normal launch`() {
        testee.onLaunched(isRestored = false, fromFreemiumScanCompleteNotification = false, freemiumLaunch = null)

        verify(pirPixelSender, never()).reportFreemiumScanCompleteNotificationOpened()
    }

    @Test
    fun `test does not report the notification opened pixel again when the dashboard is restored`() {
        testee.onLaunched(isRestored = true, fromFreemiumScanCompleteNotification = true, freemiumLaunch = null)

        verify(pirPixelSender, never()).reportFreemiumScanCompleteNotificationOpened()
    }

    @Test
    fun `test a launch without free scan attribution has no entry point`() = runTest {
        openDashboard(freemiumLaunch = null)

        assertNull(testee.freeScanEntryPoint.await())
        assertNull(testee.resolvedFreeScanEntryPoint)
    }

    @Test
    fun `test a launch from a known entry point keeps that entry point`() = runTest {
        openDashboard(FromEntryPoint(PirFreeScanEntryPoint.APP_SETTINGS_PRE_SCAN))

        assertEquals(PirFreeScanEntryPoint.APP_SETTINGS_PRE_SCAN, testee.freeScanEntryPoint.await())
        assertEquals(PirFreeScanEntryPoint.APP_SETTINGS_PRE_SCAN, testee.resolvedFreeScanEntryPoint)
    }

    @Test
    fun `test a remote message launch before any first scan result is the free scan banner`() = runTest {
        whenever(pirFreemiumDataStore.firstScanResult).thenReturn(null)

        openDashboard(FromRemoteMessage)

        assertEquals(PirFreeScanEntryPoint.FREE_SCAN_BANNER, testee.freeScanEntryPoint.await())
    }

    @Test
    fun `test a remote message launch with no matches found is the view results banner`() = runTest {
        whenever(pirFreemiumDataStore.firstScanResult).thenReturn(PirFreemiumFirstScanResult.NO_MATCHES)

        openDashboard(FromRemoteMessage)

        assertEquals(PirFreeScanEntryPoint.VIEW_RESULTS_BANNER, testee.freeScanEntryPoint.await())
    }

    @Test
    fun `test a remote message launch with matches found is the view results banner`() = runTest {
        whenever(pirFreemiumDataStore.firstScanResult).thenReturn(PirFreemiumFirstScanResult.MATCHES_FOUND)

        openDashboard(FromRemoteMessage)

        assertEquals(PirFreeScanEntryPoint.VIEW_RESULTS_BANNER, testee.freeScanEntryPoint.await())
    }

    @Test
    fun `test a launch from the scan complete notification is the notification entry point`() = runTest {
        testee.onLaunched(isRestored = false, fromFreemiumScanCompleteNotification = true, freemiumLaunch = null)

        assertEquals(PirFreeScanEntryPoint.SCAN_COMPLETE_NOTIFICATION, testee.freeScanEntryPoint.await())
        assertEquals(PirFreeScanEntryPoint.SCAN_COMPLETE_NOTIFICATION, testee.resolvedFreeScanEntryPoint)
    }

    @Test
    fun `test a notification launch restored after process death keeps the notification entry point`() = runTest {
        testee.onLaunched(
            isRestored = true,
            fromFreemiumScanCompleteNotification = true,
            freemiumLaunch = FromEntryPoint(PirFreeScanEntryPoint.SCAN_COMPLETE_NOTIFICATION),
        )

        assertEquals(PirFreeScanEntryPoint.SCAN_COMPLETE_NOTIFICATION, testee.freeScanEntryPoint.await())
    }

    @Test
    fun `test a purchase from the scan complete notification carries it in the origin`() = runTest {
        testee.onLaunched(isRestored = false, fromFreemiumScanCompleteNotification = true, freemiumLaunch = null)

        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(route)

            assertEquals(
                LaunchSubscriptionPurchase(origin = "funnel_freescan_android__scancompletenotification", featurePage = "pir"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test the entry point stays the one the dashboard was opened with after it is recreated`() = runTest {
        whenever(pirFreemiumDataStore.firstScanResult).thenReturn(null)
        openDashboard(FromRemoteMessage)

        whenever(pirFreemiumDataStore.firstScanResult).thenReturn(PirFreemiumFirstScanResult.MATCHES_FOUND)
        testee.onLaunched(isRestored = true, fromFreemiumScanCompleteNotification = false, freemiumLaunch = FromRemoteMessage)

        assertEquals(PirFreeScanEntryPoint.FREE_SCAN_BANNER, testee.freeScanEntryPoint.await())
        assertEquals(PirFreeScanEntryPoint.FREE_SCAN_BANNER, testee.resolvedFreeScanEntryPoint)
    }

    @Test
    fun `test a purchase from a known entry point carries it in the origin`() = runTest {
        openDashboard(FromEntryPoint(PirFreeScanEntryPoint.APP_SETTINGS_POST_SCAN))

        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(route)

            assertEquals(
                LaunchSubscriptionPurchase(origin = "funnel_freescan_android__appsettingspostscan", featurePage = "pir"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test a purchase from a remote message launch carries the banner in the origin`() = runTest {
        whenever(pirFreemiumDataStore.firstScanResult).thenReturn(null)
        openDashboard(FromRemoteMessage)

        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(route)

            assertEquals(
                LaunchSubscriptionPurchase(origin = "funnel_freescan_android__freescanbanner", featurePage = "pir"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test a purchase waits for the entry point when it has not resolved yet`() = runTest {
        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(route)
            expectNoEvents()

            openDashboard(FromEntryPoint(PirFreeScanEntryPoint.FREE_SCAN_BANNER))

            assertEquals(
                LaunchSubscriptionPurchase(origin = "funnel_freescan_android__freescanbanner", featurePage = "pir"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    private fun openDashboard(freemiumLaunch: PirFreemiumDashboardScreens?) {
        testee.onLaunched(isRestored = false, fromFreemiumScanCompleteNotification = false, freemiumLaunch = freemiumLaunch)
    }

    @Test
    fun `test reports dashboard opened and interaction as free for a user without a PIR subscription`() = runTest {
        whenever(pirUserUtils.hasPirSubscription()).thenReturn(false)

        testee.onStart(mock())

        verify(pirPixelSender).reportDashboardOpened(isAuthenticated = false)
        verify(pirInteractionReporter).attemptFirePixel(isAuthenticated = false)
    }

    @Test
    fun `test reports dashboard opened and interaction as paid for a user with a PIR subscription`() = runTest {
        whenever(pirUserUtils.hasPirSubscription()).thenReturn(true)

        testee.onStart(mock())

        verify(pirPixelSender).reportDashboardOpened(isAuthenticated = true)
        verify(pirInteractionReporter).attemptFirePixel(isAuthenticated = true)
    }

    @Test
    fun `test asks the dashboard to check the notification permission when onboarding saves the profile`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            testee.handleJsMessage(
                featureName = PirDashboardWebConstants.SCRIPT_FEATURE_NAME,
                method = PirDashboardWebMessages.SAVE_PROFILE.messageName,
                id = null,
                data = null,
            )

            assertEquals(Command.CheckNotificationPermission, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test ignores js messages other than save profile`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            testee.handleJsMessage(
                featureName = PirDashboardWebConstants.SCRIPT_FEATURE_NAME,
                method = PirDashboardWebMessages.START_SCAN_AND_OPT_OUT.messageName,
                id = null,
                data = null,
            )

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test does not prompt below Android 13`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            testee.onNotificationPermissionStateComputed(isAtLeastApi33 = false, permissionGranted = false, shouldShowRationale = false)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test does not prompt when the notification permission is already granted`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            testee.onNotificationPermissionStateComputed(isAtLeastApi33 = true, permissionGranted = true, shouldShowRationale = false)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test prompts to request the permission when PIR has never requested it`() = runTest {
        advanceUntilIdle()
        whenever(pirDataStore.hasRequestedNotificationPermission).thenReturn(false)

        testee.commands().test {
            testee.onNotificationPermissionStateComputed(isAtLeastApi33 = true, permissionGranted = false, shouldShowRationale = false)

            assertEquals(Command.ShowNotificationPermissionPrompt(PirNotificationPromptType.REQUEST_PERMISSION), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test prompts to request the permission when the system can still show the request`() = runTest {
        advanceUntilIdle()
        whenever(pirDataStore.hasRequestedNotificationPermission).thenReturn(true)

        testee.commands().test {
            testee.onNotificationPermissionStateComputed(isAtLeastApi33 = true, permissionGranted = false, shouldShowRationale = true)

            assertEquals(Command.ShowNotificationPermissionPrompt(PirNotificationPromptType.REQUEST_PERMISSION), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test prompts to open settings when the permission was permanently denied`() = runTest {
        advanceUntilIdle()
        whenever(pirDataStore.hasRequestedNotificationPermission).thenReturn(true)

        testee.commands().test {
            testee.onNotificationPermissionStateComputed(isAtLeastApi33 = true, permissionGranted = false, shouldShowRationale = false)

            assertEquals(Command.ShowNotificationPermissionPrompt(PirNotificationPromptType.OPEN_SETTINGS), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test reports the prompt shown pixel`() {
        testee.onNotificationPermissionPromptShown(PirNotificationPromptType.REQUEST_PERMISSION)

        verify(pirPixelSender).reportNotificationPermissionPromptShown(PirNotificationPromptType.REQUEST_PERMISSION)
    }

    @Test
    fun `test accepting the request prompt records the request and launches the system request`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            testee.onNotificationPermissionPromptAccepted(PirNotificationPromptType.REQUEST_PERMISSION)

            assertEquals(Command.RequestNotificationPermission, awaitItem())
            verify(pirDataStore).hasRequestedNotificationPermission = true
            verify(pirPixelSender).reportNotificationPermissionPromptAccepted(PirNotificationPromptType.REQUEST_PERMISSION)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test accepting the settings prompt opens the notification settings`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            testee.onNotificationPermissionPromptAccepted(PirNotificationPromptType.OPEN_SETTINGS)

            assertEquals(Command.OpenNotificationSettings, awaitItem())
            verify(pirDataStore, never()).hasRequestedNotificationPermission = any()
            verify(pirPixelSender).reportNotificationPermissionPromptAccepted(PirNotificationPromptType.OPEN_SETTINGS)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test dismissing the prompt reports the dismissed pixel without further commands`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            testee.onNotificationPermissionPromptDismissed(PirNotificationPromptType.REQUEST_PERMISSION)

            verify(pirPixelSender).reportNotificationPermissionPromptDismissed(PirNotificationPromptType.REQUEST_PERMISSION)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test reports the granted pixel only when the permission is granted`() {
        testee.onNotificationPermissionResult(granted = false)
        verify(pirPixelSender, never()).reportNotificationPermissionGranted()

        testee.onNotificationPermissionResult(granted = true)
        verify(pirPixelSender).reportNotificationPermissionGranted()
    }
}
