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

import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType.Tracker
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTracker
import com.duckduckgo.mobile.android.vpn.trackers.TrackerListProvider
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer

interface VpnTrackerDetector {

    fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer, isLocalAddress: Boolean, originatingApp: AppNameResolver.OriginatingApp): RequestTrackerType
}

class DomainBasedTrackerDetector(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val hostnameExtractor: HostnameExtractor,
    private val trackerListProvider: TrackerListProvider,
    private val vpnDatabase: VpnDatabase
) : VpnTrackerDetector {

    override fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer, isLocalAddress: Boolean, originatingApp: AppNameResolver.OriginatingApp): RequestTrackerType {
        if (isLocalAddress) {
            Timber.v("%s is a local address; not looking for trackers", packet.ip4Header.destinationAddress)
            tcb.trackerTypeDetermined = true
            tcb.isTracker = false
            return RequestTrackerType.NotTracker(packet.ip4Header.destinationAddress.hostName)
        }

        val hostname = hostnameExtractor.extract(tcb, packet, payloadBuffer)
        if (hostname == null) {
            Timber.w("Failed to determine if packet is a tracker as hostname not extracted %s", tcb.ipAndPort)
            return RequestTrackerType.Undetermined
        }

        tcb.trackerTypeDetermined = true

        trackerListProvider.findTracker(hostname)?.let { appTracker ->
            val trackerModel = Tracker(appTracker.hostname)
            if (isTrackerInExceptionRules(trackerModel, originatingApp)) {
                Timber.d("Tracker ${appTracker.hostname} is excluded in App $originatingApp")
            } else {
                tcb.isTracker = true
                tcb.trackerHostName = appTracker.hostname
                Timber.w("Determined %s to be a tracker %s", hostname, tcb.ipAndPort)
                insertTracker(appTracker)
                deviceShieldPixels.trackerBlocked()
                return Tracker(appTracker.hostname)
            }
        }

        tcb.isTracker = false
        Timber.v("Determined %s is not a tracker %s", hostname, tcb.ipAndPort)

        return RequestTrackerType.NotTracker(hostname)
    }

    private fun isTrackerInExceptionRules(tracker: Tracker, originatingApp: AppNameResolver.OriginatingApp): Boolean {
        val exceptionRule = vpnDatabase.vpnAppTrackerBlockingDao().getRuleByTrackerDomain(tracker.hostName)

        return exceptionRule != null && exceptionRule.packageNames.contains(originatingApp.packageId)
    }

    private fun insertTracker(tracker: AppTracker) {
        val vpnTracker = VpnTracker(
            trackerCompanyId = tracker.trackerCompanyId,
            company = tracker.owner.displayName,
            domain = tracker.hostname
        )
        Timber.i("Inserting $vpnTracker as tracker")
        vpnDatabase.vpnTrackerDao().insert(vpnTracker)
    }
}
