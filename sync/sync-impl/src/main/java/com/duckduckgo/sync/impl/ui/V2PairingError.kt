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
import com.duckduckgo.sync.impl.AccountErrorCodes.NEGOTIATION_ABORTED
import com.duckduckgo.sync.impl.AccountErrorCodes.NO_RECOVERY_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_REJECTED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_UNAVAILABLE
import com.duckduckgo.sync.impl.R

/**
 * Maps a v2 pairing [com.duckduckgo.sync.impl.DispatchOutcome.Failed] error code to a user-facing
 * message resource. Single source for the v2 connect-flow ViewModels so the mapping cannot diverge
 * again (divergence was the root of the silent-hang bug, 2026-06-11). NEVER returns null — every v2
 * failure surfaces a visible error. The specific `reason` string is shown as the detail line;
 * per-code copy is a follow-up.
 */
@StringRes
internal fun Int.toV2PairingErrorMessage(): Int = when (this) {
    PAIRING_REJECTED.code,
    PAIRING_UNAVAILABLE.code,
    PAIRING_CANCELLED.code,
    NEGOTIATION_ABORTED.code,
    NO_RECOVERY_CODE.code,
    PAIRING_FAILED.code,
    -> R.string.sync_connect_login_error
    else -> R.string.sync_connect_generic_error
}
