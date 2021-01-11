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

package com.duckduckgo.mobile.android.vpn.processor.tcp.requestingapp

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.perf.FirebasePerformance
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import java.net.InetAddress
import java.net.InetSocketAddress

class OriginatingAppResolver(private val connectivityManager: ConnectivityManager, private val packageManager: PackageManager) {

    fun resolveAppId(packet: Packet): RequestingApp {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val destinationPort = extractDestinationPort(packet)
            val sourcePort = extractSourcePort(packet)
            resolveModern(packet.ip4Header.destinationAddress, destinationPort, packet.ip4Header.sourceAddress, sourcePort, packet.ip4Header.protocol.number)
        } else {
            RequestingApp(UNKNOWN, "Requires Android Q")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun resolveModern(
        destinationAddress: InetAddress,
        destinationPort: Int,
        sourceAddress: InetAddress,
        sourcePort: Int,
        protocolNumber: Int
    ): RequestingApp {
        val trace = FirebasePerformance.startTrace("AppResolutionModern")
        val destination = InetSocketAddress(destinationAddress, destinationPort)
        val source = InetSocketAddress(sourceAddress, sourcePort)
        val connectionOwnerUid: Int = connectivityManager.getConnectionOwnerUid(protocolNumber, source, destination)
        val packageName = getPackageIdForUid(connectionOwnerUid)
        val appName = getAppNameForPackageId(packageName)
        val requestingApp = RequestingApp(packageName, appName)
        trace.stop()

        return requestingApp

    }

    private fun getPackageIdForUid(uid: Int): String {
        return packageManager.getNameForUid(uid) ?: UNKNOWN.also { Timber.i("Failed to get package ID for UID: $uid") }
    }

    private fun getAppNameForPackageId(packageId: String): String {
        val stripped = packageId.substringBefore(":")
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(stripped, PackageManager.GET_META_DATA)) as String
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e("Failed to find app name for: $stripped. ${e.message}")
            UNKNOWN
        }
    }

    private fun extractSourcePort(packet: Packet) =
        if (packet.isTCP) packet.tcpHeader.sourcePort else if (packet.isUDP) packet.udpHeader.sourcePort else 0

    private fun extractDestinationPort(packet: Packet) =
        if (packet.isTCP) packet.tcpHeader.destinationPort else if (packet.isUDP) packet.udpHeader.destinationPort else 0

    companion object {
        private const val UNKNOWN = "unknown"
    }
}
