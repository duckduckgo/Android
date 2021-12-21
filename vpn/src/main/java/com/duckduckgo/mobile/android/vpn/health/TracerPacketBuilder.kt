/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.health

import java.util.*
import javax.inject.Inject
import xyz.hexene.localvpn.Packet

class TracerPacketBuilder @Inject constructor() {

    fun build(): Packet {
        val tracerId = UUID.randomUUID().toString()
        return Packet.TracerPacker(tracerId)
    }
}
