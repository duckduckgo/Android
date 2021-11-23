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

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.duckduckgo.mobile.android.vpn.waitlist.RedeemCodeResult
import com.duckduckgo.mobile.android.vpn.waitlist.TrackingProtectionWaitlistManager
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@FlowPreview
@ExperimentalCoroutinesApi
class AppTPWaitlistRedeemCodeViewModelTest {

    private val manager: TrackingProtectionWaitlistManager = mock()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var viewModel: AppTPWaitlistRedeemCodeViewModel

    @Before
    fun before() {
        viewModel = AppTPWaitlistRedeemCodeViewModel(manager, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenUserSuccessfullyRedeemsCodeThenViewStateisRedeemed() = coroutineRule.runBlocking {
        whenever(manager.redeemCode("1234")).thenReturn(RedeemCodeResult.Redeemed)

        viewModel.viewState.test {
            viewModel.redeemCode("1234")
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.Idle)
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.Redeeming)
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.Redeemed)
        }
    }

    @Test
    fun whenUserRedeemsInvalidCodeThenViewStateisInvalidCode() = coroutineRule.runBlocking {
        whenever(manager.redeemCode("1234")).thenReturn(RedeemCodeResult.InvalidCode)
        viewModel.viewState.test {
            viewModel.redeemCode("1234")
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.Idle)
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.Redeeming)
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.InvalidCode)
        }
    }

    @Test
    fun whenUserRedeemsAlreadyRedeemedCodeThenViewStateisInvalidCode() = coroutineRule.runBlocking {
        whenever(manager.redeemCode("1234")).thenReturn(RedeemCodeResult.AlreadyRedeemed)
        viewModel.viewState.test {
            viewModel.redeemCode("1234")
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.Idle)
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.Redeeming)
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.InvalidCode)
        }
    }

    @Test
    fun whenRedeemCodeFailsThenViewStateIsGeneralError() = coroutineRule.runBlocking {
        whenever(manager.redeemCode("1234")).thenReturn(RedeemCodeResult.Failure)
        viewModel.viewState.test {
            viewModel.redeemCode("1234")
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.Idle)
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.Redeeming)
            assertEquals(awaitItem(), AppTPWaitlistRedeemCodeViewModel.ViewState.ErrorRedeeming)
        }
    }

}
