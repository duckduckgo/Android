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

package com.duckduckgo.sync.impl.ui

import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.CONNECT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.CREATE_ACCOUNT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.AccountErrorCodes.INVALID_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result.Error
import org.junit.Assert.assertEquals
import org.junit.Test

class V1PairingErrorTest {

    @Test
    fun whenCodeIsAlreadySignedInThenAuthenticatedDeviceMessage() {
        val content = Error(code = ALREADY_SIGNED_IN.code).toV1PairingError()
        assertEquals(R.string.sync_login_authenticated_device_error, content.message)
    }

    @Test
    fun whenCodeIsLoginFailedThenConnectLoginMessage() {
        val content = Error(code = LOGIN_FAILED.code).toV1PairingError()
        assertEquals(R.string.sync_connect_login_error, content.message)
    }

    @Test
    fun whenCodeIsConnectFailedThenConnectGenericMessage() {
        val content = Error(code = CONNECT_FAILED.code).toV1PairingError()
        assertEquals(R.string.sync_connect_generic_error, content.message)
    }

    @Test
    fun whenCodeIsCreateAccountFailedThenCreateAccountGenericMessage() {
        val content = Error(code = CREATE_ACCOUNT_FAILED.code).toV1PairingError()
        assertEquals(R.string.sync_create_account_generic_error, content.message)
    }

    @Test
    fun whenCodeIsInvalidCodeThenInvalidCodeMessage() {
        val content = Error(code = INVALID_CODE.code).toV1PairingError()
        assertEquals(R.string.sync_invalid_code_error, content.message)
    }

    @Test
    fun whenCodeIsGenericOrUnknownThenPairingFailedGenericMessage() {
        listOf(GENERIC_ERROR.code, 9999).forEach { code ->
            val content = Error(code = code).toV1PairingError()
            assertEquals("code $code message", R.string.sync_pairing_failed_generic_message, content.message)
        }
    }

    @Test
    fun whenErrorHasReasonThenReasonIsCarriedOver() {
        val content = Error(code = LOGIN_FAILED.code, reason = "connection dropped").toV1PairingError()
        assertEquals("connection dropped", content.reason)
    }
}
