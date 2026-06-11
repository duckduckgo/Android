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

import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.AccountErrorCodes.NEGOTIATION_ABORTED
import com.duckduckgo.sync.impl.AccountErrorCodes.NO_RECOVERY_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_REJECTED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_UNAVAILABLE
import com.duckduckgo.sync.impl.R
import org.junit.Assert.assertEquals
import org.junit.Test

class V2PairingErrorTest {

    @Test
    fun whenCodeIsAKnownPairingAbortThenReturnsSyncWithAnotherDeviceError() {
        listOf(
            PAIRING_REJECTED,
            PAIRING_UNAVAILABLE,
            PAIRING_CANCELLED,
            NEGOTIATION_ABORTED,
            NO_RECOVERY_CODE,
            PAIRING_FAILED,
        ).forEach { code ->
            assertEquals(
                "code $code should map to the pairing error string",
                R.string.sync_connect_login_error,
                code.code.toV2PairingErrorMessage(),
            )
        }
    }

    @Test
    fun whenCodeIsUnknownThenReturnsGenericError() {
        assertEquals(R.string.sync_connect_generic_error, GENERIC_ERROR.code.toV2PairingErrorMessage())
        assertEquals(R.string.sync_connect_generic_error, 9999.toV2PairingErrorMessage())
    }
}
