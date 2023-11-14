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
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.impl.configuration.NetPRedeemCodeError
import com.duckduckgo.networkprotection.impl.configuration.NetPRedeemCodeResponse
import com.duckduckgo.networkprotection.impl.configuration.WgVpnControllerService
import com.duckduckgo.networkprotection.impl.waitlist.store.FakeNetPWaitlistRepository
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import retrofit2.HttpException
import retrofit2.Response

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RealNetPWaitlistManagerTest {

    private val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var mockWgVpnControllerService: WgVpnControllerService

    private lateinit var netPWaitlistRepository: NetPWaitlistRepository

    @Mock
    private lateinit var networkProtectionWaitlist: NetworkProtectionWaitlist

    @Mock
    private lateinit var netPWaitlistService: NetPWaitlistService

    private val redeemErrorAdapter = Moshi.Builder().build().adapter(NetPRedeemCodeError::class.java)

    private lateinit var testee: RealNetPWaitlistManager

    @Before
    fun before() {
        netPWaitlistRepository = FakeNetPWaitlistRepository()

        MockitoAnnotations.openMocks(this)

        runBlocking {
            whenever(networkProtectionWaitlist.getState()).thenReturn(NetPWaitlistState.NotUnlocked)
        }
        testee = RealNetPWaitlistManager(
            mockWgVpnControllerService,
            netPWaitlistRepository,
            networkProtectionWaitlist,
            netPWaitlistService,
            coroutineRule.testDispatcherProvider,
            coroutineRule.testScope,
        )
    }

    @Test
    fun whenGetStateReturnStateFromRepo() = runTest {
        whenever(networkProtectionWaitlist.getState()).thenReturn(NetPWaitlistState.NotUnlocked)

        testee.getState().test {
            assertEquals(NetPWaitlistState.NotUnlocked, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRedeemCodeSuccessfulReturnRedeemed() = runTest {
        val token = NetPRedeemCodeResponse("fake_token")
        whenever(mockWgVpnControllerService.redeemCode(any())).thenReturn(token)

        assertEquals(RedeemCodeResult.Redeemed, testee.redeemCode("fake_code"))
        assertEquals(netPWaitlistRepository.getAuthenticationToken(), token.token)
    }

    @Test
    fun whenRedeemCodeErrorMessageReturnInvalid() = runTest {
        val response = convertToHTTPResponse(redeemErrorAdapter.toJson(NetPRedeemCodeError(NetPRedeemCodeError.INVALID)))
        whenever(mockWgVpnControllerService.redeemCode(any())).thenThrow(HttpException(response))

        assertEquals(RedeemCodeResult.InvalidCode, testee.redeemCode("fake_code"))

        assertNull(netPWaitlistRepository.getAuthenticationToken())
    }

    @Test
    fun whenRedeemCodeErrorUnexpectedReturnFailure() = runTest {
        val response = convertToHTTPResponse(redeemErrorAdapter.toJson(NetPRedeemCodeError("unexpected_message")))
        whenever(mockWgVpnControllerService.redeemCode(any())).thenThrow(HttpException(response))

        assertEquals(RedeemCodeResult.Failure, testee.redeemCode("fake_code"))

        assertNull(netPWaitlistRepository.getAuthenticationToken())
    }

    @Test
    fun whenRedeemCodeJSONErrorReturnInvalid() = runTest {
        val response = convertToHTTPResponse("invalid JSON")
        whenever(mockWgVpnControllerService.redeemCode(any())).thenThrow(HttpException(response))

        assertEquals(RedeemCodeResult.InvalidCode, testee.redeemCode("fake_code"))

        assertNull(netPWaitlistRepository.getAuthenticationToken())
    }

    @Test
    fun whenRedeemCodeThrowsExceptionReturnFailure() = runTest {
        whenever(mockWgVpnControllerService.redeemCode(any())).thenThrow(RuntimeException("something went wrong"))

        assertEquals(RedeemCodeResult.Failure, testee.redeemCode("fake_code"))

        assertNull(netPWaitlistRepository.getAuthenticationToken())
    }

    @Test
    fun whenUserDidNotJoinWaitlistThenDoNotCheckWaitlistStatus() = runTest {
        testee.upsertState()

        verify(netPWaitlistService, never()).waitlistStatus()
    }

    @Test
    fun whenUserIsInBetaThenDoNotCheckWaitlistStatus() = runTest {
        netPWaitlistRepository.setAuthenticationToken("token")
        netPWaitlistRepository.setWaitlistToken("token")

        testee.upsertState()

        verify(netPWaitlistService, never()).waitlistStatus()
    }

    @Test
    fun whenUserJoinWaitlistAndNotInBetaThenCheckWaitlistStatus() = runTest {
        netPWaitlistRepository.setWaitlistToken("token")
        testee.upsertState()

        verify(netPWaitlistService, times(1)).waitlistStatus()
    }

    private fun convertToHTTPResponse(jsonResponse: String): Response<String> {
        val errorResponseBody = jsonResponse.toResponseBody("application/json".toMediaTypeOrNull())
        return Response.error(403, errorResponseBody)
    }
}
