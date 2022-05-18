/*
 * Copyright (c) 2022 DuckDuckGo
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
import com.duckduckgo.app.email.ui.EmailWebViewViewModel.Command.EmailSignEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class EmailWebViewViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockEmailManager: EmailManager = mock()
    private lateinit var testee: EmailWebViewViewModel

    @Test
    fun whenEmailSignedInThenEmailSignEventCommandSent() = runTest {
        val emailStateFlow = MutableStateFlow(true)
        whenever(mockEmailManager.signedInFlow()).thenReturn(emailStateFlow.asStateFlow())
        testee = EmailWebViewViewModel(mockEmailManager)

        testee.commands.test {
            assertEquals(EmailSignEvent, awaitItem())
        }
    }

    @Test
    fun whenEmailSignedOutThenEmailSignEventCommandSent() = runTest {
        val emailStateFlow = MutableStateFlow(false)
        whenever(mockEmailManager.signedInFlow()).thenReturn(emailStateFlow.asStateFlow())
        testee = EmailWebViewViewModel(mockEmailManager)

        testee.commands.test {
            assertEquals(EmailSignEvent, awaitItem())
        }
    }
}
