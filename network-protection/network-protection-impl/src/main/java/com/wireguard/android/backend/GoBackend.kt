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
import androidx.annotation.RequiresApi
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.library.loader.LibraryLoader
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType
import dagger.SingleInstanceIn
import java.net.InetSocketAddress
import javax.inject.Inject
import kotlin.system.exitProcess
import timber.log.Timber

@SingleInstanceIn(VpnScope::class)
class GoBackend @Inject constructor(
    private val context: Context,
    private val appTrackerRepository: AppTrackerRepository,
    vpnDatabase: VpnDatabase,
) {
    private val uidPackageIdMap = mutableMapOf<Int, String>()
    private val vpnAppTrackerBlockingDao = vpnDatabase.vpnAppTrackerBlockingDao()

    init {
        try {
            Timber.v("Loading wireguard-go library")
            LibraryLoader.loadLibrary(context, "wg-go")
        } catch (ignored: Throwable) {
            Timber.e(ignored, "Error loading wireguard-go library")
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
    ): Int

    external fun wgVersion(): String

    // TODO: ALL duplicate code below - move to a util class

    // Called from native code
    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("unused")
    fun shouldAllow(protocol: Int, saddr: String, sport: Int, daddr: String, dport: Int, sni: String): Boolean {
        Timber.v(
            """
            shouldAllow called for
              protocol=$protocol
              saddr=$saddr
              sport=$sport
              daddr=$daddr
              dport=$dport
              sni=$sni
            """.trimIndent(),
        )

        if (protocol != 6 /* TCP */ && protocol != 17 /* UDP */) return true

        val cm = context.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager? ?: return true

        val local = InetSocketAddress(saddr, sport)
        val remote = InetSocketAddress(daddr, dport)

        Timber.v("Get uid local=$local remote=$remote")
        val uid = cm.getConnectionOwnerUid(protocol, local, remote)

        val packageId = uidPackageIdMap[uid] ?: getPackageIdForUid(uid)
        uidPackageIdMap[uid] = packageId

        val allow = shouldAllowDomain(sni, packageId)
        Timber.v("Got uid=$uid ($packageId). Allow = $allow")
        return allow
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

    private fun shouldAllowDomain(name: String, packageId: String): Boolean {
        // TODO:
        // if (VpnExclusionList.isDdgApp(packageId)) {
        //     Timber.v("shouldAllowDomain: DDG ap is always allowed")
        //     return true
        // }

        val type = appTrackerRepository.findTracker(name, packageId)

        if (type is AppTrackerType.ThirdParty && !isTrackerInExceptionRules(packageId = packageId, hostname = name)) {
            Timber.d("shouldAllowDomain for $name/$packageId = false")

            // TODO:
            // val trackingApp = appNamesCache[packageId] ?: appNameResolver.getAppNameForPackageId(packageId)
            // appNamesCache.put(packageId, trackingApp)
            //
            // // if the app name is unknown, do not block
            // if (trackingApp.isUnknown()) return true

            // VpnTracker(
            //     trackerCompanyId = type.tracker.trackerCompanyId,
            //     company = type.tracker.owner.name,
            //     companyDisplayName = type.tracker.owner.displayName,
            //     domain = type.tracker.hostname,
            //     trackingApp = TrackingApp(trackingApp.packageId, trackingApp.appName)
            // ).run {
            //     appTrackerRecorder.insertTracker(this)
            // }
            return false
        }

        return true
    }

    private fun isTrackerInExceptionRules(packageId: String, hostname: String): Boolean {
        return vpnAppTrackerBlockingDao.getRuleByTrackerDomain(hostname)?.let { rule ->
            Timber.d("isTrackerInExceptionRules: found rule $rule for $hostname")
            return rule.packageNames.contains(packageId)
        } ?: false
    }
}
