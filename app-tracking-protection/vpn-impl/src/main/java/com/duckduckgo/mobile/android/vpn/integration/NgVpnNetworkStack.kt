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

package com.duckduckgo.mobile.android.vpn.integration

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.mobile.android.vpn.apps.VpnExclusionList
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.AppTrackerRecorder
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType
import com.duckduckgo.vpn.network.api.*
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

// Make this class private to make sure we don't use it for now
// we'll use it once we start testing the new VPN networking layer
private class NgVpnNetworkStack constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val vpnNetwork: Lazy<VpnNetwork>,
    private val appTrackerRepository: AppTrackerRepository,
    private val appNameResolver: AppNameResolver,
    private val appTrackerRecorder: AppTrackerRecorder,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    vpnDatabase: VpnDatabase,
) : VpnNetworkStack, VpnNetworkCallback {

    private val packageManager = context.packageManager
    private val vpnAppTrackerBlockingDao = vpnDatabase.vpnAppTrackerBlockingDao()

    private var tunnelThread: Thread? = null
    private var jniContext = 0L
    private val jniLock = Any()
    private val dnsResourceCache = LruCache<String, String>(1000)
    // cache packageId -> app name
    private val appNamesCache = LruCache<String, AppNameResolver.OriginatingApp>(100)

    override val name: String = "ng"

    override fun onCreateVpn(): Result<Unit> {
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }

        vpnNetwork.addCallback(this)

        if (jniContext != 0L) {
            vpnNetwork.stop(jniContext)
            synchronized(jniLock) {
                vpnNetwork.destroy(jniContext)
                jniContext = 0
            }

        }
        jniContext = vpnNetwork.create()

        return Result.success(Unit)
    }

    override fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit> {
        return startNative(tunfd.fd)
    }

    override fun onStopVpn(): Result<Unit> {
        return stopNative()
    }

    override fun onDestroyVpn(): Result<Unit> {
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }

        synchronized(jniLock) {
            vpnNetwork.destroy(jniContext)
            jniContext = 0
        }
        vpnNetwork.addCallback(null)

        return Result.success(Unit)
    }

    override fun mtu(): Int {
        return vpnNetwork.get().mtu()
    }

    override fun onExit(reason: String) {
        Timber.w("Native exit reason=$reason")
        // restart the VPN
        appCoroutineScope.launch {
            TrackerBlockingVpnService.restartVpnService(context, forceGc = true)
        }
    }

    override fun onError(errorCode: Int, message: String) {
        Timber.w("onError $errorCode:$message")
    }

    override fun onDnsResolved(dnsRR: DnsRR) {
        dnsResourceCache.put(dnsRR.resource, dnsRR.qName)
        Timber.w("dnsResolved called for $dnsRR")
    }

    override fun onSniResolved(sniRR: SniRR) {
        dnsResourceCache.put(sniRR.resource, sniRR.name)
        Timber.w("sniResolved called for ${sniRR.name} / ${sniRR.resource}")
    }

    override fun isDomainBlocked(domainRR: DomainRR): Boolean {
        Timber.w("isDomainBlocked for $domainRR")
        return !shouldAllowDomain(domainRR.name, domainRR.uid)
    }

    override fun isAddressBlocked(addressRR: AddressRR): Boolean {
        val hostname = dnsResourceCache[addressRR.address] ?: return false
        val domainAllowed = shouldAllowDomain(hostname, addressRR.uid)
        Timber.w("isAddressBlocked for $addressRR ($hostname) = ${!domainAllowed}")
        return !domainAllowed
    }

    private fun shouldAllowDomain(name: String, uid: Int): Boolean {
        val packageId = getPackageIdForUid(uid)

        if (VpnExclusionList.isDdgApp(packageId)) {
            Timber.v("shouldAllowDomain: DDG ap is always allowed")
            return true
        }

        val type = appTrackerRepository.findTracker(name, packageId)

        if (type is AppTrackerType.ThirdParty && !isTrackerInExceptionRules(packageId = packageId, hostname = name)) {
            Timber.d("shouldAllowDomain for $name/$packageId = false")
            val trackingApp = appNamesCache[packageId] ?: appNameResolver.getAppNameForPackageId(packageId)
            appNamesCache.put(packageId, trackingApp)

            // if the app name is unknown, skip inserting the tracker but still block the tracker
            if (trackingApp.isUnknown()) return false

            VpnTracker(
                trackerCompanyId = type.tracker.trackerCompanyId,
                company = type.tracker.owner.name,
                companyDisplayName = type.tracker.owner.displayName,
                domain = type.tracker.hostname,
                trackingApp = TrackingApp(trackingApp.packageId, trackingApp.appName)
            ).run {
                appTrackerRecorder.insertTracker(this)
            }
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

    private fun getPackageIdForUid(uid: Int): String {
        val packages: Array<String>?

        try {
            packages = packageManager.getPackagesForUid(uid)
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

    private fun startNative(tunfd: Int): Result<Unit> {
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }
        if (tunnelThread == null) {
            Timber.d("Start native runtime")
            val level = if (appBuildConfig.isDebug || appBuildConfig.isInternalBuild()) VpnNetworkLog.DEBUG else VpnNetworkLog.ASSERT
            vpnNetwork.start(jniContext, level)

            tunnelThread = Thread {
                Timber.d("Running tunnel in context $jniContext")
                vpnNetwork.run(jniContext, tunfd)
                Timber.w("Tunnel exited")
                tunnelThread = null
            }.also { it.start() }

            Timber.d("Started tunnel thread")
        }

        return Result.success(Unit)
    }

    private fun stopNative(): Result<Unit> {
        Timber.d("Stop native runtime")
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }

        tunnelThread?.let {
            Timber.d("Stopping tunnel thread")

            vpnNetwork.stop(jniContext)

            var thread = tunnelThread
            while (thread != null && thread.isAlive) {
                try {
                    Timber.v("Joining tunnel thread context $jniContext")
                    thread.join()
                } catch (t: InterruptedException) {
                    Timber.d("Joined tunnel thread")
                }
                thread = tunnelThread
            }
            tunnelThread = null

            Timber.d("Stopped tunnel thread")
        }

        return Result.success(Unit)
    }

    private fun Lazy<VpnNetwork>.safeGet(): Result<VpnNetwork> {
        return runCatching {
            get()
        }.onFailure {
            Timber.e(it, "Failed to get VpnNetwork")
            Result.failure<VpnNetwork>(it)
        }
    }
}
