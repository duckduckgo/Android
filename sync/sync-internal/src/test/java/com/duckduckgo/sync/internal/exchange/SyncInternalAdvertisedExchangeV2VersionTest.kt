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

package com.duckduckgo.sync.internal.exchange

import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.RealAdvertisedExchangeV2Version
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SyncInternalAdvertisedExchangeV2VersionTest {

    private val real: RealAdvertisedExchangeV2Version = mock()
    private val advertisedVersion = SyncInternalAdvertisedExchangeV2Version(real)

    @Test fun `no override delegates to the real resolver`() {
        whenever(real.resolve()).thenReturn(ExchangeProtocolVersion.V2_1)
        advertisedVersion.overrideFlow.value = null

        assertEquals(ExchangeProtocolVersion.V2_1, advertisedVersion.resolve())
    }

    @Test fun `override forces the selected version regardless of flags`() {
        advertisedVersion.overrideFlow.value = ExchangeProtocolVersion.V2_1

        assertEquals(ExchangeProtocolVersion.V2_1, advertisedVersion.resolve())
        verifyNoInteractions(real)
    }
}
