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

import android.content.Context
import androidx.annotation.StringRes
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind

@StringRes
internal fun syncV2ConfirmationMessageRes(
    unknownPeer: Boolean,
    peerKind: PeerKind?,
): Int {
    val thirdParty = peerKind == PeerKind.THIRD_PARTY
    return when {
        unknownPeer && thirdParty -> R.string.sync_v2_confirmation_message_unknown_peer_3p
        unknownPeer -> R.string.sync_v2_confirmation_message_unknown_peer_ddg
        thirdParty -> R.string.sync_v2_confirmation_message_3p
        else -> R.string.sync_v2_confirmation_message_ddg
    }
}

internal fun Context.syncV2ConfirmationMessage(
    peerName: String?,
    peerKind: PeerKind?,
): String {
    val res = syncV2ConfirmationMessageRes(unknownPeer = peerName.isNullOrBlank(), peerKind = peerKind)
    return if (peerName.isNullOrBlank()) getString(res) else getString(res, peerName)
}
