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

package com.duckduckgo.mobile.android.vpn.processor.tcp.tracker

import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType.Tracker
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer
import javax.inject.Inject

interface VpnTrackerDetector {

    fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer, isLocalAddress: Boolean): RequestTrackerType
}

class DomainBasedTrackerDetector @Inject constructor(
    private val hostnameExtractor: HostnameExtractor,
    private val trackerListProvider: TrackerListProvider,
    private val vpnDatabase: VpnDatabase
) : VpnTrackerDetector {

    override fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer, isLocalAddress: Boolean): RequestTrackerType {
        if (isLocalAddress) {
            Timber.v("%s is a local address; not looking for trackers", packet.ip4Header.destinationAddress)
            tcb.trackerTypeDetermined = true
            tcb.isTracker = false
            return RequestTrackerType.NotTracker
        }

        val hostname = hostnameExtractor.extract(tcb, packet, payloadBuffer)
        if (hostname == null) {
            Timber.w("Failed to determine if packet is a tracker as hostname not extracted %s", tcb.ipAndPort)
            return RequestTrackerType.Undetermined
        }

        tcb.trackerTypeDetermined = true

        trackerListProvider.trackerList().forEach { tracker ->
            if (hostname.endsWith(tracker.hostname)) {
                tcb.isTracker = true
                Timber.w("Determined %s to be a tracker %s", hostname, tcb.ipAndPort)
                insertTracker(tracker)
                return Tracker
            }
        }

        tcb.isTracker = false
        Timber.v("Determined %s is not a tracker %s", hostname, tcb.ipAndPort)

        return RequestTrackerType.NotTracker
    }

    private fun insertTracker(tracker: TrackerListProvider.Tracker) {
        val trackerCompany = TrackerListProvider.TRACKER_GROUP_COMPANIES.find { it.trackerCompanyId == tracker.trackerCompanyId }
            ?: TrackerListProvider.UNDEFINED_TRACKER_COMPANY
        val vpnTracker = VpnTracker(trackerCompanyId = trackerCompany.trackerCompanyId, domain = tracker.hostname)
        Timber.i("Inserting $tracker as tracker")
        vpnDatabase.vpnTrackerDao().insert(vpnTracker)
    }
}
