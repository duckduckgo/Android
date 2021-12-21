/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.store

import timber.log.Timber

const val PACKET_TYPE_UDP = "UDP"
const val PACKET_TYPE_TCP = "TCP"

interface PacketPersister {
    fun persistDataSent(packetLength: Int, packetType: String)
    fun persistDataReceived(packetLength: Int, packetType: String)
}

class RoomPacketPersister(val vpnDatabase: VpnDatabase) : PacketPersister {

    override fun persistDataSent(packetLength: Int, packetType: String) {
        Timber.v("[%s] Updating data sent: %d bytes of data", packetType, packetLength)
        vpnDatabase.vpnDataStatsDao().upsertDataSent(packetLength)
    }

    override fun persistDataReceived(packetLength: Int, packetType: String) {
        Timber.v("[%s] Updating data received: %d bytes of data", packetType, packetLength)
        vpnDatabase.vpnDataStatsDao().upsertDataReceived(packetLength)
    }
}

class DummyPacketPersister : PacketPersister {

    override fun persistDataSent(packetLength: Int, packetType: String) {
        Timber.v("[%s] Updating data sent: %d bytes of data", packetType, packetLength)
    }

    override fun persistDataReceived(packetLength: Int, packetType: String) {
        Timber.v("[%s] Updating data received: %d bytes of data", packetType, packetLength)
    }
}
