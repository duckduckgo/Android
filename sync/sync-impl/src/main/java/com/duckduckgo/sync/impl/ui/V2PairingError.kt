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

import androidx.annotation.StringRes
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_REJECTED
import com.duckduckgo.sync.impl.AccountErrorCodes.THIRD_PARTY_ALREADY_UPGRADED
import com.duckduckgo.sync.impl.R

/** Title + message for a v2 pairing dialog. */
internal data class V2PairingErrorContent(
    @StringRes val title: Int,
    @StringRes val message: Int,
)

/**
 * Maps a v2 pairing [com.duckduckgo.sync.impl.DispatchOutcome.Failed] error code to dialog copy.
 * Single source for the v2 connect-flow ViewModels so the mapping cannot diverge again (divergence
 * was the root of the silent-hang bug, 2026-06-11). NEVER returns null. Distinct error codes
 * intentionally collapse to a small set of user messages here; the codes survive for logs/telemetry.
 */
internal fun Int.toV2PairingError(): V2PairingErrorContent = when (this) {
    PAIRING_REJECTED.code -> V2PairingErrorContent(R.string.sync_dialog_error_title, R.string.sync_v2_error_pairing_rejected)
    PAIRING_CANCELLED.code -> V2PairingErrorContent(R.string.sync_dialog_error_title, R.string.sync_v2_error_pairing_canceled)
    // Joined from a 3rd-party recovery code but the account already holds a ddg credential — can't re-upgrade.
    THIRD_PARTY_ALREADY_UPGRADED.code -> V2PairingErrorContent(R.string.sync_dialog_error_title, R.string.sync_v2_error_third_party_already_upgraded)
    else -> V2PairingErrorContent(R.string.sync_dialog_error_title, R.string.sync_v2_error_pairing_failed)
}

/** Fixed copy for the non-Failed [com.duckduckgo.sync.impl.DispatchOutcome.UpgradeRequired] outcome. */
internal val v2UpgradeRequiredError = V2PairingErrorContent(
    title = R.string.sync_dialog_error_title,
    message = R.string.sync_v2_error_upgrade_required,
)
