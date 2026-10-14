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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * The exchange protocol version this device offers a pairing session. Everything version-dependent
 * (e.g. whether relay calls carry the channel-secret `Authorization` header) derives from it inside
 * [ExchangeV2Runner], which reads it once per session, at bootstrap, so answers only take effect on
 * the next session.
 */
interface AdvertisedExchangeV2Version {
    /** The highest exchange protocol version this device advertises to peers. */
    fun resolve(): ExchangeProtocolVersion.V2
}

@ContributesBinding(AppScope::class)
class RealAdvertisedExchangeV2Version @Inject constructor(
    private val syncFeature: SyncFeature,
) : AdvertisedExchangeV2Version {

    override fun resolve(): ExchangeProtocolVersion.V2 = if (syncFeature.canUseExchangeV2Point1().isEnabled()) {
        ExchangeProtocolVersion.V2_1
    } else {
        ExchangeProtocolVersion.V2_0
    }
}
