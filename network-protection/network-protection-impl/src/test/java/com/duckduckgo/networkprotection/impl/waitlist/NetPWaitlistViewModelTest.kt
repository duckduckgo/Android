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

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistState.CodeRedeemed
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistState.NotJoinedQueue
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistViewModel.Command
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class NetPWaitlistViewModelTest {

    @Mock
    private lateinit var mockNetPWaitlistManager: NetPWaitlistManager

    private lateinit var testee: NetPWaitlistViewModel

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockNetPWaitlistManager.getState()).thenReturn(NotJoinedQueue)

        testee = NetPWaitlistViewModel(mockNetPWaitlistManager)
    }

    @Test
    fun whenViewModelCreatedThenEmitWaitlistState() = runTest {
        testee.viewState.test {
            assert(awaitItem().waitlist is NotJoinedQueue)
        }
    }

    @Test
    fun whenHaveInviteClickedThenEmitCommandRedeemCode() = runTest {
        testee.commands.test {
            testee.haveAnInviteCode()
            Assert.assertEquals(Command.EnterInviteCode, awaitItem())
        }
    }

    @Test
    fun whenCodeRedeemFailedThenEmitNotJoinedState() = runTest {
        testee.viewState.test {
            testee.onCodeRedeemed(Activity.RESULT_CANCELED)
            assert(awaitItem().waitlist is NotJoinedQueue)
        }
    }

    @Test
    fun whenCodeRedeemSucceedsThenEmitRedeemedState() = runTest {
        testee.viewState.test {
            assert(awaitItem().waitlist is NotJoinedQueue)
            testee.onCodeRedeemed(Activity.RESULT_OK)
            assert(awaitItem().waitlist is CodeRedeemed)
        }
    }

    @Test
    fun whenGetStartedClickedThenEmitCommandOpenNetP() = runTest {
        testee.commands.test {
            testee.getStarted()
            Assert.assertEquals(Command.OpenNetP, awaitItem())
        }
    }
}
