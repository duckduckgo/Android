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
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTracker
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType.FirstParty
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType.ThirdParty
import com.duckduckgo.mobile.android.vpn.trackers.TrackerListProvider
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer

interface VpnTrackerDetector {

    fun determinePacketType(
        tcb: TCB,
        packet: Packet,
        payloadBuffer: ByteBuffer,
        isLocalAddress: Boolean,
        requestingApp: AppNameResolver.OriginatingApp
    ): RequestTrackerType
}

class DomainBasedTrackerDetector(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val hostnameExtractor: HostnameExtractor,
    private val trackerListProvider: TrackerListProvider,
    private val vpnDatabase: VpnDatabase
) : VpnTrackerDetector {

    override fun determinePacketType(
        tcb: TCB,
        packet: Packet,
        payloadBuffer: ByteBuffer,
        isLocalAddress: Boolean,
        requestingApp: AppNameResolver.OriginatingApp
    ): RequestTrackerType {

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

        return when (val trackerType = trackerListProvider.findTracker(hostname, requestingApp.packageId)) {
            is FirstParty -> {
                Timber.d("Determined %s to be a 1st party tracker for %s, both owned by %s [%s]", hostname, requestingApp.packageId, trackerType.tracker.owner.name, tcb.ipAndPort)
                tcb.isTracker = false
                RequestTrackerType.NotTracker(hostname)
            }
            is ThirdParty -> {
                val trackerHostname = trackerType.tracker.hostname
                val trackerModel = RequestTrackerType.Tracker(trackerHostname)
                return if (isTrackerInExceptionRules(trackerModel, requestingApp)) {
                    Timber.d("Tracker $trackerHostname is excluded in App $requestingApp")
                    tcb.isTracker = false
                    RequestTrackerType.NotTracker(trackerHostname)
                } else {
                    tcb.isTracker = true
                    tcb.trackerHostName = trackerHostname
                    Timber.i("Determined %s to be a 3rd party tracker for %s, tracker owned by %s [%s]", hostname, requestingApp.packageId, trackerType.tracker.owner.name, tcb.ipAndPort)
                    insertTracker(trackerType.tracker)
                    deviceShieldPixels.trackerBlocked()
                    RequestTrackerType.Tracker(trackerType.tracker.hostname)
                    RequestTrackerType.Tracker(trackerHostname)
                }
            }
            else -> {
                tcb.isTracker = false
                Timber.v("Determined %s is not a tracker %s", hostname, tcb.ipAndPort)
                RequestTrackerType.NotTracker(hostname)
            }
        }
    }

    private fun isTrackerInExceptionRules(tracker: RequestTrackerType.Tracker, originatingApp: AppNameResolver.OriginatingApp): Boolean {
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
