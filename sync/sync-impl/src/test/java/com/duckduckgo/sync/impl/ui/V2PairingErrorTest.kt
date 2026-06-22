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
import com.duckduckgo.sync.impl.AccountErrorCodes.THIRD_PARTY_ALREADY_UPGRADED
import com.duckduckgo.sync.impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V2PairingErrorTest {

    @Test
    fun whenCodeIsPairingRejectedThenCanceledFromOtherDeviceTitleWithTryAgain() {
        val content = PAIRING_REJECTED.code.toV2PairingError()
        assertEquals(R.string.sync_v2_error_pairing_rejected, content.title)
        assertEquals(R.string.sync_v2_error_try_again, content.message)
    }

    @Test
    fun whenCodeIsPairingCancelledThenCanceledTitleOnly() {
        val content = PAIRING_CANCELLED.code.toV2PairingError()
        assertEquals(R.string.sync_v2_error_pairing_canceled, content.title)
        assertNull(content.message)
    }

    @Test
    fun whenCodeIsThirdPartyAlreadyUpgradedThenSyncFailedTitleWithUpgradeMessage() {
        val content = THIRD_PARTY_ALREADY_UPGRADED.code.toV2PairingError()
        assertEquals(R.string.sync_v2_error_pairing_failed, content.title)
        assertEquals(R.string.sync_v2_error_third_party_already_upgraded, content.message)
    }

    @Test
    fun whenCodeIsAnyOtherAbortThenSyncFailedTitleWithTryAgain() {
        listOf(PAIRING_UNAVAILABLE, NEGOTIATION_ABORTED, NO_RECOVERY_CODE, PAIRING_FAILED).forEach { code ->
            val content = code.code.toV2PairingError()
            assertEquals("code $code title", R.string.sync_v2_error_pairing_failed, content.title)
            assertEquals("code $code message", R.string.sync_v2_error_try_again, content.message)
        }
    }

    @Test
    fun whenCodeIsGenericOrUnknownThenSyncFailedTitleWithTryAgain() {
        listOf(GENERIC_ERROR.code, 9999).forEach { code ->
            val content = code.toV2PairingError()
            assertEquals(R.string.sync_v2_error_pairing_failed, content.title)
            assertEquals(R.string.sync_v2_error_try_again, content.message)
        }
    }

    @Test
    fun whenUpgradeRequiredThenUpdateBrowserTitleOnly() {
        assertEquals(R.string.sync_v2_error_upgrade_required, v2UpgradeRequiredError.title)
        assertNull(v2UpgradeRequiredError.message)
    }

    @Test
    fun whenAlreadyPairedThenAlreadySyncedTitleWithMessage() {
        assertEquals(R.string.sync_v2_already_paired_title, v2AlreadyPairedError.title)
        assertEquals(R.string.sync_v2_already_paired_message, v2AlreadyPairedError.message)
    }
}
