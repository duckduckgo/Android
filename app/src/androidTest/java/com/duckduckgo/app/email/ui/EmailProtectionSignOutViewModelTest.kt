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
import com.duckduckgo.app.email.ui.EmailProtectionSignOutViewModel.Command.*
import com.duckduckgo.app.runBlocking
import org.junit.Rule
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.junit.Before
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@FlowPreview
@ExperimentalCoroutinesApi
class EmailProtectionSignOutViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockEmailManager: EmailManager = mock()
    private lateinit var testee: EmailProtectionSignOutViewModel

    @Before
    fun setup() {
        testee = EmailProtectionSignOutViewModel(mockEmailManager)
    }

    @Test
    fun whenSignOutThenEmitSignOutCommand() = coroutineRule.runBlocking {
        testee.commands.test {
            testee.signOut()
            assertEquals(SignOut, expectItem())
        }
    }

    @Test
    fun whenOnEmailLogoutThenEmitCloseScreenCommand() = coroutineRule.runBlocking {
        testee.commands.test {
            testee.onEmailLogout()
            assertEquals(CloseScreen, expectItem())
        }
    }

    @Test
    fun whenOnEmailLogoutThenCallSignOut() {
        testee.onEmailLogout()
        verify(mockEmailManager).signOut()
    }
}
