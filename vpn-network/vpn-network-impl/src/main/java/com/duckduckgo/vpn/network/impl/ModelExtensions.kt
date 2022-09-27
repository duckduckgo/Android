/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.vpn.network.impl

import com.duckduckgo.vpn.network.api.AddressRR
import com.duckduckgo.vpn.network.api.DnsRR
import com.duckduckgo.vpn.network.impl.models.Packet
import com.duckduckgo.vpn.network.impl.models.ResourceRecord

internal fun ResourceRecord.toDnsRR(): DnsRR {
    return DnsRR(Time, QName.orEmpty(), AName.orEmpty(), Resource.orEmpty(), TTL)
}

internal fun Packet.toAddressRR(): AddressRR {
    return AddressRR(daddr.orEmpty(), uid)
}
