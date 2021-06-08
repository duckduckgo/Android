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
    fun whenOnEmailSettingClickedAndUserIsSignedInThenEmitLaunchEmailSignOutCommandWithCorrectEmailAddress() = coroutineTestRule.runBlocking {
        testee.commandsFlow.test {
            givenUserIsSignedIn()
            testee.onEmailSettingClicked()

            assertEquals(BetaFeaturesViewModel.Command.LaunchEmailSignOut("test@duck.com"), expectItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnEmailSettingClickedAndUserIsSignedInWithNoEmailThenEmitLaunchEmailSignInCommand() = coroutineTestRule.runBlocking {
        testee.commandsFlow.test {
            givenUserIsSignedInEmailIsNull()
            testee.onEmailSettingClicked()

            assertEquals(BetaFeaturesViewModel.Command.LaunchEmailSignIn, expectItem())

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

    private fun givenUserIsSignedIn() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("test@duck.com")
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
    }

    private fun givenUserIsSignedInEmailIsNull() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn(null)
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
    }
}
