/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.waitlist.trackerprotection.ui

import android.app.Activity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.cash.turbine.Event
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.waitlist.trackerprotection.AppTPWaitlistWorkRequestBuilder
import com.duckduckgo.app.waitlist.trackerprotection.AppTPWaitlistWorker
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.waitlist.TrackingProtectionWaitlistManager
import com.duckduckgo.mobile.android.vpn.waitlist.WaitlistState
import com.duckduckgo.mobile.android.vpn.waitlist.api.*
import com.nhaarman.mockitokotlin2.*
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalTime
@FlowPreview
@ExperimentalCoroutinesApi
class AppTPWaitlistViewModelTest {

    private val manager: TrackingProtectionWaitlistManager = mock()
    private var service: AppTrackingProtectionWaitlistService = mock()
    private var workManager: WorkManager = mock()
    private val waitlistBuilder: AppTPWaitlistWorkRequestBuilder = mock()
    private val deviceShieldPixels: DeviceShieldPixels = mock()

    private lateinit var viewModel: AppTPWaitlistViewModel

    @get:Rule var coroutineRule = CoroutineTestRule()

    @Before
    fun before() {
        whenever(manager.waitlistState()).thenReturn(WaitlistState.NotJoinedQueue)
        viewModel =
            AppTPWaitlistViewModel(
                manager, service, workManager, waitlistBuilder, deviceShieldPixels)
    }

    @Test
    fun whenUserJoinsWaitlistSuccesfullyThenNotificationDialogIsShown() =
        coroutineRule.runBlocking {
            val success = WaitlistResponse("token", 1221321321)
            whenever(service.joinWaitlist()).thenReturn(success)

            val expectedWorkRequest = OneTimeWorkRequestBuilder<AppTPWaitlistWorker>().build()
            whenever(waitlistBuilder.waitlistRequestWork(false)).thenReturn(expectedWorkRequest)

            viewModel.commands.test {
                viewModel.joinTheWaitlist()

                verify(manager).joinWaitlist(any(), any())
                verify(workManager).enqueue(expectedWorkRequest)
                verify(deviceShieldPixels).didShowWaitlistDialog()

                assert(awaitItem() is AppTPWaitlistViewModel.Command.ShowNotificationDialog)
            }
        }

    @Test
    fun whenUserJoinsWaitlistResponseDoesntHaveTokenThenErrorMessageisShown() =
        coroutineRule.runBlocking {
            val success = WaitlistResponse("", 1221321321)
            whenever(service.joinWaitlist()).thenReturn(success)

            viewModel.commands.test {
                viewModel.joinTheWaitlist()
                assert(awaitItem() is AppTPWaitlistViewModel.Command.ShowErrorMessage)
            }
        }

    @Test
    fun whenUserJoinsWaitlistResponseDoesntHaveTimestampThenErrorMessageisShown() =
        coroutineRule.runBlocking {
            val success = WaitlistResponse("token", null)
            whenever(service.joinWaitlist()).thenReturn(success)

            viewModel.commands.test {
                viewModel.joinTheWaitlist()
                assert(awaitItem() is AppTPWaitlistViewModel.Command.ShowErrorMessage)
            }
        }

    @Test
    fun whenUserCantJoinsWaitlistThenErrorMessageIsShown() =
        coroutineRule.runBlocking {
            viewModel =
                AppTPWaitlistViewModel(
                    manager,
                    FailWaitlistService(),
                    workManager,
                    waitlistBuilder,
                    deviceShieldPixels)

            viewModel.commands.test {
                viewModel.joinTheWaitlist()
                assert(awaitItem() is AppTPWaitlistViewModel.Command.ShowErrorMessage)
            }
        }

    @Test
    fun whenViewModelCreatedThenWaitlistStateIsNotJoined() =
        coroutineRule.runBlocking {
            viewModel.viewState.test {
                assert(awaitItem().waitlist is WaitlistState.NotJoinedQueue)
            }
        }

    @Test
    fun whenUserHasAnInviteCodeThenEnterInviteCodeCommandSent() =
        coroutineRule.runBlocking {
            viewModel.commands.test {
                viewModel.haveAnInviteCode()
                assert(awaitItem() is AppTPWaitlistViewModel.Command.EnterInviteCode)
            }
        }

    @Test
    fun whenUserWantsToLearnMoreThenOpenUrlCommandSent() =
        coroutineRule.runBlocking {
            viewModel.commands.test {
                viewModel.learnMore()
                assertEquals(AppTPWaitlistViewModel.Command.LaunchBetaInstructions, awaitItem())
            }
        }

    @Test
    fun whenUserWantsToBeNotifiedThenWaitlistManagerStoresIt() =
        coroutineRule.runBlocking {
            viewModel.onNotifyMeClicked()

            verify(manager).notifyOnJoinedWaitlist()
            verify(deviceShieldPixels).didPressWaitlistDialogNotifyMe()
        }

    @Test
    fun whenUserWantsToGetStartedThenShowOnboardingCommandSent() =
        coroutineRule.runBlocking {
            viewModel.commands.test {
                viewModel.getStarted()
                assert(awaitItem() is AppTPWaitlistViewModel.Command.ShowOnboarding)
            }
        }

    @Test
    fun whenDialogdismissedThenWaitlistStateIsJoinedQueue() =
        coroutineRule.runBlocking {
            val waitlistState = WaitlistState.JoinedQueue(false)
            whenever(manager.waitlistState()).thenReturn(waitlistState)
            viewModel.viewState.test {
                assertEquals(
                    Event.Item(AppTPWaitlistViewModel.ViewState(WaitlistState.NotJoinedQueue)),
                    awaitEvent())
                viewModel.onDialogDismissed()

                verify(deviceShieldPixels, never()).didPressWaitlistDialogDismiss()
                assertEquals(
                    Event.Item(AppTPWaitlistViewModel.ViewState(waitlistState)), awaitEvent())
            }
        }

    @Test
    fun whenUserDismissedDialogThenWaitlistStateIsJoinedQueue() =
        coroutineRule.runBlocking {
            viewModel.onNoThanksClicked()
            verify(deviceShieldPixels).didPressWaitlistDialogDismiss()
        }

    @Test
    fun whenCodeRedeemSuccessThenWaitlistStateIsCodeRedeemed() =
        coroutineRule.runBlocking {
            val waitlistState = WaitlistState.CodeRedeemed
            whenever(manager.waitlistState()).thenReturn(waitlistState)
            viewModel.viewState.test {
                assertEquals(
                    Event.Item(AppTPWaitlistViewModel.ViewState(WaitlistState.NotJoinedQueue)),
                    awaitEvent())
                viewModel.onCodeRedeemed(Activity.RESULT_OK)
                assertEquals(
                    Event.Item(AppTPWaitlistViewModel.ViewState(waitlistState)), awaitEvent())
            }
        }

    @Test
    fun whenCodeRedeemFailsThenWaitlistStateIsCodeRedeemed() =
        coroutineRule.runBlocking {
            val waitlistState = WaitlistState.CodeRedeemed
            whenever(manager.waitlistState()).thenReturn(waitlistState)
            viewModel.viewState.test {
                assertEquals(
                    Event.Item(AppTPWaitlistViewModel.ViewState(WaitlistState.NotJoinedQueue)),
                    awaitEvent())
                viewModel.onCodeRedeemed(Activity.RESULT_CANCELED)
                expectNoEvents()
            }
        }

    class FailWaitlistService : AppTrackingProtectionWaitlistService {

        override suspend fun joinWaitlist(): WaitlistResponse {
            throw java.lang.Exception()
        }
        override suspend fun waitlistStatus(): WaitlistStatusResponse = WaitlistStatusResponse(1234)
        override suspend fun getCode(token: String): AppTPInviteCodeResponse =
            AppTPInviteCodeResponse("token")
        override suspend fun redeemCode(code: String): AppTPRedeemCodeResponse {
            TODO("Not yet implemented")
        }
    }
}
