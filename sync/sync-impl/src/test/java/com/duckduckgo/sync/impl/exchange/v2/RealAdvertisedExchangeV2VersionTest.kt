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

package com.duckduckgo.sync.impl.exchange.v2

import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class RealAdvertisedExchangeV2VersionTest {

    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val advertisedVersion = RealAdvertisedExchangeV2Version(syncFeature)

    @Test fun `advertises v2_1 when the version flag is enabled`() {
        syncFeature.canUseExchangeV2Point1().setRawStoredState(State(remoteEnableState = true))

        assertEquals(ExchangeProtocolVersion.V2_1, advertisedVersion.resolve())
    }

    @Test fun `advertises v2_0 when the version flag is disabled`() {
        syncFeature.canUseExchangeV2Point1().setRawStoredState(State(remoteEnableState = false))

        assertEquals(ExchangeProtocolVersion.V2_0, advertisedVersion.resolve())
    }
}
