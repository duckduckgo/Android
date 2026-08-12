/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.sync.TestSyncFixtures.token
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Call
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class SetKeysIfAbsentCallTest {

    private val syncService: SyncService = mock()
    val retrofitCall: Call<SetKeyIfAbsentResponse> = mock()
    private val call = RealSetKeysIfAbsentCall(syncService)

    private val accountInfoKey = ProtectedKeyEntry(
        kid = "k-account-info",
        purpose = "account_info",
        encryptedWith = "ddg",
        encryptedPrivateKey = "AAAA",
        publicKey = RsaJwk(n = "mod", e = "AQAB"),
    )

    @Test
    fun whenResponseIs201ThenReturnCreated() {
        stubResponse(Response.success(201, SetKeyIfAbsentResponse(keys = emptyList())))

        val result = call.execute(token, "account_info", listOf(accountInfoKey))

        assertEquals(Result.Success(SetKeysIfAbsentResult.Created), result)
    }

    @Test
    fun whenResponseIs200ThenReturnExistingWithServerKey() {
        val existingKey = AccountInfoKeyWire(kid = "server-kid", publicKey = RsaJwk(n = "server-mod", e = "AQAB"))
        stubResponse(Response.success(200, SetKeyIfAbsentResponse(keys = listOf(existingKey))))

        val result = call.execute(token, "account_info", listOf(accountInfoKey))

        assertEquals(Result.Success(SetKeysIfAbsentResult.Existing(kid = "server-kid", publicKey = RsaJwk(n = "server-mod", e = "AQAB"))), result)
    }

    @Test
    fun whenResponseIs200WithNoKeyThenReturnExistsFetchRequired() {
        stubResponse(Response.success(200, SetKeyIfAbsentResponse(keys = emptyList())))

        val result = call.execute(token, "account_info", listOf(accountInfoKey))

        assertEquals(Result.Success(SetKeysIfAbsentResult.ExistsFetchRequired), result)
    }

    @Test
    fun whenResponseIs409ThenReturnExistsFetchRequired() {
        stubResponse(Response.error(409, """{"error":"conflict"}""".toResponseBody("application/json".toMediaTypeOrNull())))

        val result = call.execute(token, "account_info", listOf(accountInfoKey))

        assertEquals(Result.Success(SetKeysIfAbsentResult.ExistsFetchRequired), result)
    }

    @Test
    fun whenResponseIsErrorThenReturnUnexpectedStatusCode() {
        stubResponse(
            Response.error(
                500,
                """{"error":"server error"}""".toResponseBody("application/json".toMediaTypeOrNull()),
            ),
        )

        val result = call.execute(token, "account_info", listOf(accountInfoKey))

        assertEquals(Result.Error(code = 500, reason = "unexpected status code"), result)
    }

    @Test
    fun whenCallThrowsThenReturnInternalError() {
        whenever(syncService.setKeysIfAbsent(anyString(), eq("account_info"), any())).thenReturn(retrofitCall)
        whenever(retrofitCall.execute()).thenThrow(RuntimeException("Network error"))

        val result = call.execute(token, "account_info", listOf(accountInfoKey))

        assertEquals(Result.Error(reason = "internal error"), result)
    }

    private fun stubResponse(response: Response<SetKeyIfAbsentResponse>) {
        whenever(syncService.setKeysIfAbsent(anyString(), eq("account_info"), any())).thenReturn(retrofitCall)
        whenever(retrofitCall.execute()).thenReturn(response)
    }
}
