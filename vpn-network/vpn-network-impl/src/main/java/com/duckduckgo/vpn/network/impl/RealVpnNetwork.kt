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

import android.content.Context
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.library.loader.LibraryLoader
import com.duckduckgo.vpn.network.api.*
import com.duckduckgo.vpn.network.impl.models.Allowed
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import com.duckduckgo.vpn.network.impl.models.Packet
import com.duckduckgo.vpn.network.impl.models.ResourceRecord
import com.duckduckgo.vpn.network.impl.models.Usage
import timber.log.Timber
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.system.exitProcess

@ContributesBinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class RealVpnNetwork @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val context: Context,
) : VpnNetwork {

    private external fun jni_init(sdk: Int): Long

    private external fun jni_start(context: Long, loglevel: Int)

    private external fun jni_run(context: Long, tun: Int, fwd53: Boolean, rcode: Int)

    private external fun jni_stop(context: Long)

    private external fun jni_clear(context: Long)

    private external fun jni_get_mtu(): Int

    private external fun jni_get_stats(context: Long): IntArray?

    private external fun jni_pcap(name: String, record_size: Int, file_size: Int)

    private external fun jni_socks5(addr: String, port: Int, username: String, password: String)

    private external fun jni_done(context: Long)

    private val uidPackageIdMap = mutableMapOf<Int, String>()

    private var callback = AtomicReference<VpnNetworkCallback?>(null)

    override fun create(): Long {
        return jni_init(appBuildConfig.sdkInt)
    }

    override fun start(contextId: Long, logLevel: VpnNetworkLog) {
        jni_socks5("", 0, "", "")
        jni_start(contextId, logLevel.toAndroidLevel())
    }

    override fun run(contextId: Long, tunfd: Int) {
        jni_run(contextId, tunfd, true, 3)
    }

    override fun stop(contextId: Long) {
        jni_stop(contextId)
    }

    override fun destroy(contextId: Long) {
//        jni_clear(contextId)
        jni_done(contextId)
    }

    override fun mtu(): Int {
        return jni_get_mtu()
    }

    override fun addCallback(callback: VpnNetworkCallback?) {
        this.callback.set(callback)
    }

    // Called from native code
    @Suppress("unused")
    private fun nativeExit(reason: String) {
        Timber.v("Native exit reason=$reason")
        callback.get()?.onExit(reason)
    }

    // Called from native code
    @Suppress("unused")
    private fun nativeError(error: Int, message: String) {
        Timber.v("Native error $error:$message")
        callback.get()?.onError(error, message)
    }

    // Called from native code
    @Suppress("unused")
    private fun logPacket(packet: Packet) {
        Timber.v("Log packet called for $packet")
    }

    // Called from native code
    @Suppress("unused")
    private fun dnsResolved(rr: ResourceRecord) {
        Timber.v("dnsResolved called for $rr")
        callback.get()?.onDnsResolved(rr.toDnsRR())
    }

    // Called from native code
    @Suppress("unused")
    private fun sniResolved(name: String, resource: String) {
        Timber.v("sniResolved called for $name / $resource")
        callback.get()?.onSniResolved(SniRR(name, resource))
    }

    // Called from native code
    @Suppress("unused")
    private fun isDomainBlocked(name: String, uid: Int): Boolean {
        Timber.v("isDomainBlocked for $name ($uid)")
        return callback.get()?.isDomainBlocked(DomainRR(name, uid)) ?: false
    }

    // Called from native code
    @Suppress("unused")
    private fun isAddressAllowed(packet: Packet): Allowed? {
        packet.allowed = true

        packet.allowed = (callback.get()?.isAddressBlocked(packet.toAddressRR()) == false)

        Timber.v("isAddressAllowed for $packet = ${packet.allowed}")

        return if (packet.allowed) Allowed() else null
    }

    // Called from native code
    @Suppress("unused")
    private fun accountUsage(usage: Usage) {
        Timber.v("accountUsage $usage")
    }

    // Called from native code
    @Suppress("unused")
    private fun protect(socket: Int): Boolean {
        Timber.v("protect socket")
        return true
    }

    // Called from native code
    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("unused")
    private fun getUidQ(version: Int, protocol: Int, saddr: String, sport: Int, daddr: String, dport: Int): Int {
        Timber.v(
            """
            getUidQ called for
              version=$version
              protocol=$protocol
              saddr=$saddr
              sport=$sport
              daddr=$daddr
              dport=$dport
            """.trimIndent()
        )

        if (protocol != 6 /* TCP */ && protocol != 17 /* UDP */) return Process.INVALID_UID

        val cm = context.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager? ?: return Process.INVALID_UID

        val local = InetSocketAddress(saddr, sport)
        val remote = InetSocketAddress(daddr, dport)

        Timber.v("Get uid local=$local remote=$remote")
        val uid = cm.getConnectionOwnerUid(protocol, local, remote)
        uidPackageIdMap[uid]?.let { packageId ->
            Timber.v("Returned cached uid=$uid ($packageId)")
            return uid
        }
        val packageId = getPackageIdForUid(uid)
        uidPackageIdMap[uid] = packageId
        Timber.v("Get uid=$uid ($packageId)")
        return uid
    }

    private fun getPackageIdForUid(uid: Int): String {
        val packages: Array<String>?

        try {
            packages = context.packageManager.getPackagesForUid(uid)
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to get package ID for UID: $uid due to security violation.")
            return "unknown"
        }

        if (packages.isNullOrEmpty()) {
            Timber.w("Failed to get package ID for UID: $uid")
            return "unknown"
        }

        if (packages.size > 1) {
            val sb = StringBuilder(String.format("Found %d packages for uid:%d", packages.size, uid))
            packages.forEach {
                sb.append(String.format("\npackage: %s", it))
            }
            Timber.d(sb.toString())
        }

        return packages.first()
    }

    init {
        try {
            LibraryLoader.loadLibrary(context, "netguard")
        } catch (ignored: Throwable) {
            Timber.e(ignored, "Error loading netguard library")
            exitProcess(1)
        }
    }
}

private fun VpnNetworkLog.toAndroidLevel(): Int {
    return when (this) {
        VpnNetworkLog.ASSERT -> Log.ASSERT
        VpnNetworkLog.ERROR -> Log.ERROR
        VpnNetworkLog.WARN -> Log.WARN
        VpnNetworkLog.INFO -> Log.INFO
        VpnNetworkLog.DEBUG -> Log.DEBUG
        VpnNetworkLog.VERBOSE -> Log.VERBOSE
    }
}
