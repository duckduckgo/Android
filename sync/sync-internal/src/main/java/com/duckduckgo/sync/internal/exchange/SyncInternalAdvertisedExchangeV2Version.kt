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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.AdvertisedExchangeV2Version
import com.duckduckgo.sync.impl.exchange.v2.RealAdvertisedExchangeV2Version
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, replaces = [RealAdvertisedExchangeV2Version::class])
class SyncInternalAdvertisedExchangeV2Version @Inject constructor(
    private val real: RealAdvertisedExchangeV2Version,
) : AdvertisedExchangeV2Version {

    val overrideFlow = MutableStateFlow<ExchangeProtocolVersion.V2?>(null)

    override fun resolve() = overrideFlow.value ?: defaultVersion()

    fun defaultVersion() = real.resolve()
}
