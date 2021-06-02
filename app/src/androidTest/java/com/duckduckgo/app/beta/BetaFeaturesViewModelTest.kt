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

package com.duckduckgo.app.beta

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
@ExperimentalTime
class BetaFeaturesViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: BetaFeaturesViewModel

    @Mock
    private lateinit var mockEmailManager: EmailManager

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        testee = BetaFeaturesViewModel(mockEmailManager)
    }

    @Test
    fun whenUserNotInQueueThenEmitJoinWaitlistState() = coroutineTestRule.runBlocking {
        givenUserIsNotInQueue()
        testee.resume()
        testee.viewFlow.test {
            assert(expectItem().emailState is BetaFeaturesViewModel.EmailState.JoinWaitlist)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserInQueueThenEmitDisabledState() = coroutineTestRule.runBlocking {
        givenUserIsInQueue()
        testee.resume()
        testee.viewFlow.test {
            assert(expectItem().emailState is BetaFeaturesViewModel.EmailState.Disabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserInBetaAndNotSignedInThenEmitDisabledState() = coroutineTestRule.runBlocking {
        givenUserIsInBeta()
        testee.resume()
        testee.viewFlow.test {
            assert(expectItem().emailState is BetaFeaturesViewModel.EmailState.Disabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserInBetaAndSignedInAndHasEmailThenEmitEnabledState() = coroutineTestRule.runBlocking {
        givenUserIsInBetaAndSignedIn()
        testee.resume()
        testee.viewFlow.test {
            assert(expectItem().emailState is BetaFeaturesViewModel.EmailState.Enabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserIsSignedInAndHasEmailThenEmitEnabledState() = coroutineTestRule.runBlocking {
        givenUserIsSignedInAndHasAliasAvailable()
        testee.resume()
        testee.viewFlow.test {
            assert(expectItem().emailState is BetaFeaturesViewModel.EmailState.Enabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserIsSignedInAndEmailAddressIsNullThenEmitDisabledState() = coroutineTestRule.runBlocking {
        givenUserIsSignedInAndDoesNotHaveAliasAvailable()
        testee.resume()
        testee.viewFlow.test {
            assert(expectItem().emailState is BetaFeaturesViewModel.EmailState.Disabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnEmailSettingClickedAndUserIsSignedInThenEmitLaunchEmailSignOutCommandWithCorrectEmailAddress() = coroutineTestRule.runBlocking {
        testee.commandsFlow.test {
            givenUserIsSignedInAndHasAliasAvailable()
            testee.onEmailSettingClicked()

            assertEquals(BetaFeaturesViewModel.Command.LaunchEmailSignOut("test@duck.com"), expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnEmailSettingClickedAndUserIsNotSignedInThenEmitLaunchEmailSignInCommand() = coroutineTestRule.runBlocking {
        testee.commandsFlow.test {
            givenUserIsNotSignedIn()
            testee.onEmailSettingClicked()

            assertEquals(BetaFeaturesViewModel.Command.LaunchEmailSignIn, expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    private fun givenUserIsNotSignedIn() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
    }

    private fun givenUserIsSignedInAndHasAliasAvailable() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("test@duck.com")
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
    }

    private fun givenUserIsSignedInAndDoesNotHaveAliasAvailable() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn(null)
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.InBeta)
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
    }

    private fun givenUserIsNotInQueue() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.NotJoinedQueue)
    }

    private fun givenUserIsInQueue() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.JoinedQueue)
    }

    private fun givenUserIsInBeta() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.InBeta)
    }

    private fun givenUserIsInBetaAndSignedIn() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("test@duck.com")
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        whenever(mockEmailManager.waitlistState()).thenReturn(AppEmailManager.WaitlistState.InBeta)
    }
}
