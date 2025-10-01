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

package com.wireguard.android.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.library.loader.LibraryLoader
import com.duckduckgo.mobile.android.app.tracking.AppTrackerDetector
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import dagger.Lazy
import dagger.SingleInstanceIn
import java.net.InetSocketAddress
import javax.inject.Inject
import kotlin.system.exitProcess
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

@SingleInstanceIn(VpnScope::class)
class GoBackend @Inject constructor(
    private val context: Context,
    private val appTrackerDetector: AppTrackerDetector,
    pixels: Lazy<NetworkProtectionPixels>,
) {
    init {
        try {
            logcat { "Loading wireguard-go library" }
            LibraryLoader.loadLibrary(context, "wg-go")
        } catch (ignored: Throwable) {
            pixels.get().reportWireguardLibraryLoadFailed()
            Thread.sleep(100)
            logcat(ERROR) { "Error loading wireguard-go library: ${ignored.asLog()}" }
            exitProcess(1)
        }
    }

    external fun wgGetConfig(handle: Int): String?

    external fun wgGetSocketV4(handle: Int): Int

    external fun wgGetSocketV6(handle: Int): Int

    external fun wgTurnOff(handle: Int)

    external fun wgTurnOn(
        ifName: String,
        tunFd: Int,
        settings: String,
        androidLogLevel: Int,
        sdk: Int,
    ): Int

    external fun wgVersion(): String

    /**
     * Configure (enable/disable) the PCAP functionality. When enable the network layer will record a PCAP file
     * with every outbound IP packet before being encrypted by the VPN tunnel and any inbound decrypted IP packet.
     *
     * @param filename is the pcap filename. If `null` is passed, PCAP will be disabled
     * @param recordSize is the snap length
     * @param fileSize is the max size of the PCAP file. File will always be truncated to max value
     */
    external fun wgPcap(filename: String?, recordSize: Int, fileSize: Int)

    // Called from native code
    @Suppress("unused")
    fun shouldAllow(protocol: Int, saddr: String, sport: Int, daddr: String, dport: Int, sni: String, uid: Int): Boolean {
        logcat {
            """
            shouldAllow called for
              protocol=$protocol
              saddr=$saddr
              sport=$sport
              daddr=$daddr
              dport=$dport
              sni=$sni
              uid=$uid
            """.trimIndent()
        }

        @Suppress("ktlint:standard:comment-wrapping")
        if (protocol != 6 /* TCP */ && protocol != 17 /* UDP */) return true

        @Suppress("NAME_SHADOWING")
        val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getUidQ(protocol, saddr, sport, daddr, dport)
        } else {
            uid
        }

        return shouldAllowDomain(sni, uid)
    }

    // Called from native code
    @Suppress("unused")
    fun recordMalwareBlock(domain: String) {
        logcat { "Malware blocked in $domain" }
    }

    private fun shouldAllowDomain(
        name: String,
        uid: Int,
    ): Boolean {
        val tracker = appTrackerDetector.evaluate(name, uid)
        logcat { "shouldAllowDomain for $name ($uid) = $tracker" }

        return tracker == null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getUidQ(protocol: Int, saddr: String, sport: Int, daddr: String, dport: Int): Int {
        val cm = context.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager? ?: return Process.INVALID_UID

        val local = InetSocketAddress(saddr, sport)
        val remote = InetSocketAddress(daddr, dport)

        logcat { "Get uid local=$local remote=$remote" }
        return cm.getConnectionOwnerUid(protocol, local, remote)
    }
}
