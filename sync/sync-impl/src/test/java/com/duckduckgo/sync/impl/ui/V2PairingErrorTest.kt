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
import org.junit.Test

class V2PairingErrorTest {

    @Test
    fun whenCodeIsPairingRejectedThenCanceledFromOtherDeviceCopy() {
        val content = PAIRING_REJECTED.code.toV2PairingError()
        assertEquals(R.string.sync_dialog_error_title, content.title)
        assertEquals(R.string.sync_v2_error_pairing_rejected, content.message)
    }

    @Test
    fun whenCodeIsPairingCancelledThenCanceledCopy() {
        val content = PAIRING_CANCELLED.code.toV2PairingError()
        assertEquals(R.string.sync_dialog_error_title, content.title)
        assertEquals(R.string.sync_v2_error_pairing_canceled, content.message)
    }

    @Test
    fun whenCodeIsThirdPartyAlreadyUpgradedThenSyncFromConnectedBrowserCopy() {
        val content = THIRD_PARTY_ALREADY_UPGRADED.code.toV2PairingError()
        assertEquals(R.string.sync_v2_error_pairing_failed, content.title)
        assertEquals(R.string.sync_v2_error_third_party_already_upgraded, content.message)
    }

    @Test
    fun whenCodeIsAnyOtherAbortThenSyncFailedCopy() {
        listOf(PAIRING_UNAVAILABLE, NEGOTIATION_ABORTED, NO_RECOVERY_CODE, PAIRING_FAILED).forEach { code ->
            assertEquals(
                "code $code should map to Sync failed.",
                R.string.sync_v2_error_pairing_failed,
                code.code.toV2PairingError().message,
            )
        }
    }

    @Test
    fun whenCodeIsGenericOrUnknownThenSyncFailedCopy() {
        assertEquals(R.string.sync_v2_error_pairing_failed, GENERIC_ERROR.code.toV2PairingError().message)
        assertEquals(R.string.sync_v2_error_pairing_failed, 9999.toV2PairingError().message)
    }

    @Test
    fun whenUpgradeRequiredThenUpdateBrowserCopy() {
        assertEquals(R.string.sync_dialog_error_title, v2UpgradeRequiredError.title)
        assertEquals(R.string.sync_v2_error_upgrade_required, v2UpgradeRequiredError.message)
    }
}
