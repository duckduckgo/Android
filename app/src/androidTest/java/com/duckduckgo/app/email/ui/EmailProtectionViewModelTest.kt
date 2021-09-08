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

package com.duckduckgo.app.email.ui

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class EmailProtectionViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockEmailManager: EmailManager = mock()
    private val emailStateFlow = MutableStateFlow(false)
    private lateinit var testee: EmailProtectionViewModel

    @Before
    fun before() {
        whenever(mockEmailManager.signedInFlow()).thenReturn(emailStateFlow.asStateFlow())
        whenever(mockEmailManager.getEmailAddress()).thenReturn("test@duck.com")
        whenever(mockEmailManager.isEmailFeatureSupported()).thenReturn(true)
        testee = EmailProtectionViewModel(mockEmailManager)
    }

    @Test
    fun whenViewModelCreatedThenEmitEmailState() = coroutineRule.runBlocking {
        testee.viewState.test {
            assert(expectItem().emailState is EmailProtectionViewModel.EmailState.SignedOut)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSignedInIfFeatureIsSupportedAndFlowEmitsFalseThenViewStateFlowEmitsEmailState() = coroutineRule.runBlocking {
        testee.viewState.test {
            emailStateFlow.emit(false)
            (expectItem().emailState is EmailProtectionViewModel.EmailState.SignedIn)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSignedInIfFeatureIsSupportedAndFlowEmitsTrueThenViewStateFlowEmitsEmailState() = coroutineRule.runBlocking {
        testee.viewState.test {
            emailStateFlow.emit(true)
            assert(expectItem().emailState is EmailProtectionViewModel.EmailState.SignedOut)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFeatureIsNotSupportedThenEmitNotSupportedState() = coroutineRule.runBlocking {
        whenever(mockEmailManager.isEmailFeatureSupported()).thenReturn(false)
        testee.viewState.test {
            emailStateFlow.emit(true)
            assert(expectItem().emailState is EmailProtectionViewModel.EmailState.NotSupported)

            cancelAndConsumeRemainingEvents()
        }
    }

}
