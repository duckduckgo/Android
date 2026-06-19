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

import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncV2ConfirmationCopyTest {

    @Test fun whenKnownDdgPeerThenFullDataList() {
        assertEquals(
            R.string.sync_v2_confirmation_message_ddg,
            syncV2ConfirmationMessageRes(unknownPeer = false, peerKind = PeerKind.DDG),
        )
    }

    @Test fun whenKnownThirdPartyPeerThenChatsOnly() {
        assertEquals(
            R.string.sync_v2_confirmation_message_3p,
            syncV2ConfirmationMessageRes(unknownPeer = false, peerKind = PeerKind.THIRD_PARTY),
        )
    }

    @Test fun whenUnknownDdgPeerThenFullDataListWithoutName() {
        assertEquals(
            R.string.sync_v2_confirmation_message_unknown_peer_ddg,
            syncV2ConfirmationMessageRes(unknownPeer = true, peerKind = PeerKind.DDG),
        )
    }

    @Test fun whenUnknownThirdPartyPeerThenChatsOnlyWithoutName() {
        assertEquals(
            R.string.sync_v2_confirmation_message_unknown_peer_3p,
            syncV2ConfirmationMessageRes(unknownPeer = true, peerKind = PeerKind.THIRD_PARTY),
        )
    }

    @Test fun whenKindNullThenFallsBackToFullDdgList() {
        assertEquals(
            R.string.sync_v2_confirmation_message_ddg,
            syncV2ConfirmationMessageRes(unknownPeer = false, peerKind = null),
        )
        assertEquals(
            R.string.sync_v2_confirmation_message_unknown_peer_ddg,
            syncV2ConfirmationMessageRes(unknownPeer = true, peerKind = null),
        )
    }
}
