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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistRedeemCodeViewModel.ViewState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class NetPWaitlistRedeemCodeViewModelTest {

    @Mock
    private lateinit var mockNetPWaitlistManager: NetPWaitlistManager

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: NetPWaitlistRedeemCodeViewModel

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        testee = NetPWaitlistRedeemCodeViewModel(mockNetPWaitlistManager, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenCodeInvalidEmitCorrectState() = runTest {
        whenever(mockNetPWaitlistManager.redeemCode(any())).thenReturn(RedeemCodeResult.InvalidCode)

        testee.viewState.test {
            testee.redeemCode("fake_code")

            assertEquals(awaitItem(), ViewState.Idle)
            assertEquals(awaitItem(), ViewState.InvalidCode)
        }
    }

    @Test
    fun whenCodeRedeemedEmitCorrectState() = runTest {
        whenever(mockNetPWaitlistManager.redeemCode(any())).thenReturn(RedeemCodeResult.Redeemed)

        testee.viewState.test {
            testee.redeemCode("fake_code")

            assertEquals(awaitItem(), ViewState.Idle)
            assertEquals(awaitItem(), ViewState.Redeemed)
        }
    }

    @Test
    fun whenCodeRedeemFailureEmitCorrectState() = runTest {
        whenever(mockNetPWaitlistManager.redeemCode(any())).thenReturn(RedeemCodeResult.Failure)

        testee.viewState.test {
            testee.redeemCode("fake_code")

            assertEquals(awaitItem(), ViewState.Idle)
            assertEquals(awaitItem(), ViewState.ErrorRedeeming)
        }
    }
}
