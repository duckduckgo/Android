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

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver.OriginatingApp
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.ContentTypeExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.PayloadBytesExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.TlsContentType.*
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTracker
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType.FirstParty
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType.ThirdParty
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
        requestingApp: OriginatingApp
    ): RequestTrackerType
}

class DomainBasedTrackerDetector(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val hostnameExtractor: HostnameExtractor,
    private val appTrackerRepository: AppTrackerRepository,
    private val appTrackerRecorder: AppTrackerRecorder,
    private val payloadBytesExtractor: PayloadBytesExtractor,
    private val tlsContentTypeExtractor: ContentTypeExtractor,
    private val vpnDatabase: VpnDatabase,
    private val requestInterceptors: PluginPoint<VpnTrackerDetectorInterceptor>,
) : VpnTrackerDetector {

    override fun determinePacketType(
        tcb: TCB,
        packet: Packet,
        payloadBuffer: ByteBuffer,
        isLocalAddress: Boolean,
        requestingApp: OriginatingApp
    ): RequestTrackerType {

        if (isLocalAddress) {
            Timber.v("%s is a local address; not looking for trackers", packet.ip4Header.destinationAddress)
            tcb.trackerTypeDetermined = true
            tcb.isTracker = false

            return RequestTrackerType.NotTracker(packet.ip4Header.destinationAddress.hostName)
        }

        val payloadBytes = payloadBytesExtractor.extract(packet, payloadBuffer)
        val hostname = hostnameExtractor.extract(tcb, payloadBytes)
        if (hostname == null) {
            Timber.w("Failed to determine if packet is a tracker as hostname not extracted %s", tcb.ipAndPort)
            return RequestTrackerType.Undetermined
        }

        for (interceptor in requestInterceptors.getPlugins()) {
            interceptor.interceptTrackerRequest(hostname, requestingApp.packageId)?.let { return it }
        }

        return when (val trackerType = appTrackerRepository.findTracker(hostname, requestingApp.packageId)) {
            is FirstParty -> {
                Timber.d(
                    "Determined %s to be a 1st party tracker for %s, both owned by %s [%s]",
                    hostname,
                    requestingApp.packageId,
                    trackerType.tracker.owner.name,
                    tcb.ipAndPort
                )
                tcb.trackerTypeDetermined = true
                tcb.isTracker = false
                RequestTrackerType.NotTracker(hostname)
            }
            is ThirdParty -> {
                val trackerHostname = trackerType.tracker.hostname
                val trackerModel = RequestTrackerType.Tracker(trackerHostname)
                return if (isTrackerInExceptionRules(trackerModel, requestingApp)) {
                    Timber.d("Tracker %s is excluded in App %s", trackerHostname, requestingApp)
                    tcb.trackerTypeDetermined = true
                    tcb.isTracker = false
                    RequestTrackerType.NotTracker(trackerHostname)
                } else {
                    tcb.trackerHostName = trackerHostname

                    when (tlsContentTypeExtractor.isTlsApplicationData(payloadBytes)) {
                        Undetermined -> {
                            Timber.v("Unable to determine TLS content type, fallback to blocking as if it were application data %s", tcb.ipAndPort)
                            tcb.trackerTypeDetermined = true
                            tcb.isTracker = true
                            recordTrackerBlocked(trackerType, tcb, requestingApp)
                            RequestTrackerType.Tracker(trackerHostname)
                        }
                        TlsApplicationData -> {
                            Timber.v("This is TLS content type: Application Data and is a 3rd party tracker. We'll block this. %s", tcb.ipAndPort)
                            tcb.trackerTypeDetermined = true
                            tcb.isTracker = true
                            recordTrackerBlocked(trackerType, tcb, requestingApp)
                            RequestTrackerType.Tracker(trackerHostname)
                        }
                        NotApplicationData -> {
                            // this is request for a tracker, but we don't want to block the TLS handshake, so treat this as a non-tracker for now
                            tcb.trackerTypeDetermined = false
                            Timber.v("This is a TLS message but isn't ApplicationData. We'll allow this for now %s", tcb.ipAndPort)
                            RequestTrackerType.TrackerDelayedBlock(trackerHostname)
                        }
                    }
                }
            }
            else -> {
                tcb.trackerTypeDetermined = true
                tcb.isTracker = false
                Timber.v("Determined %s is not a tracker %s", hostname, tcb.ipAndPort)
                RequestTrackerType.NotTracker(hostname)
            }
        }
    }

    private fun recordTrackerBlocked(trackerType: ThirdParty, tcb: TCB, requestingApp: OriginatingApp) {
        Timber.i(
            "Determined %s to be a 3rd party tracker for %s, tracker owned by %s [%s]",
            tcb.hostName,
            requestingApp.packageId,
            trackerType.tracker.owner.name,
            tcb.ipAndPort
        )
        insertTracker(trackerType.tracker, requestingApp)
        deviceShieldPixels.trackerBlocked()
    }

    private fun isTrackerInExceptionRules(tracker: RequestTrackerType.Tracker, originatingApp: OriginatingApp): Boolean {
        val exceptionRule = vpnDatabase.vpnAppTrackerBlockingDao().getRuleByTrackerDomain(tracker.hostName)

        return exceptionRule != null && exceptionRule.packageNames.contains(originatingApp.packageId)
    }

    private fun insertTracker(tracker: AppTracker, requestingApp: OriginatingApp) {
        if (requestingApp.isInvalid()) {
            // FIXME exclude false positive of DDG app
            // we don't yet know the reason why the DDG app appears sometimes in the list of of tracking apps
            // for now we manually exclude while we investigate the root cause
            Timber.d("Originating app is either DDG or UNKNOWN, skipping db insertion for %s (%s)", requestingApp.appName, requestingApp.packageId)
            return
        }

        val vpnTracker = VpnTracker(
            trackerCompanyId = tracker.trackerCompanyId,
            company = tracker.owner.name,
            companyDisplayName = tracker.owner.displayName,
            trackingApp = TrackingApp(requestingApp.packageId, requestingApp.appName),
            domain = tracker.hostname
        )

        appTrackerRecorder.insertTracker(vpnTracker)
    }

    private fun OriginatingApp.isInvalid() = isDdg() || isUnknown()
}
