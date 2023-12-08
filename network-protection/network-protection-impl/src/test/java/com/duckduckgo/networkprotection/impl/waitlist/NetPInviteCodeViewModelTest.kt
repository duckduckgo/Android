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

package com.duckduckgo.networkprotection.impl.waitlist

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.impl.waitlist.NetPInviteCodeViewModel.Command
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class NetPInviteCodeViewModelTest {

    @Mock
    private lateinit var mockNetPWaitlistManager: NetPWaitlistManager

    private lateinit var viewModel: NetPInviteCodeViewModel

    private val flow = MutableStateFlow<NetPWaitlistState>(NotUnlocked)

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockNetPWaitlistManager.getState()).thenReturn(flow)

        viewModel = NetPInviteCodeViewModel(mockNetPWaitlistManager, mock<NetPWaitlistCodeNotification>())
    }

    @Test
    fun whenViewModelCreatedThenEmitWaitlistState() = runTest {
        viewModel.viewState.test {
            assert(awaitItem().waitlist is NotUnlocked)
        }
    }

    @Test
    fun whenHaveInviteClickedThenEmitCommandRedeemCode() = runTest {
        viewModel.commands.test {
            viewModel.haveAnInviteCode()
            Assert.assertEquals(Command.EnterInviteCode, awaitItem())
        }
    }

    @Test
    fun whenGetStartedClickedThenEmitCommandOpenNetP() = runTest {
        viewModel.commands.test {
            viewModel.getStarted()
            Assert.assertEquals(Command.OpenTermsScreen, awaitItem())
        }
    }
}
