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
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.apps.VpnExclusionList
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
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
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnNetworkStack::class
)
@ContributesBinding(
    scope = VpnScope::class,
    boundType = VpnNetworkCallback::class
)
@SingleInstanceIn(VpnScope::class)
class NgVpnNetworkStack @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val vpnNetwork: VpnNetwork,
    private val appTrackerRepository: AppTrackerRepository,
    private val appNameResolver: AppNameResolver,
    private val appTrackerRecorder: AppTrackerRecorder,
    private val appTpFeatureConfig: AppTpFeatureConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val vpnNetworkStackVariantManager: VpnNetworkStackVariantManager,
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

    override fun isEnabled(): Boolean {
        val variant = vpnNetworkStackVariantManager.getVariant()
        return appTpFeatureConfig.isEnabled(AppTpSetting.VpnNewNetworkingLayer) && variant == name
    }

    override fun shouldSetActiveNetworkDnsServers(): Boolean {
        // for native VPN network layer always set active network DNS
        return shouldSetUnderlyingNetworks()
    }

    override fun shouldSetUnderlyingNetworks(): Boolean {
        return appTpFeatureConfig.isEnabled(AppTpSetting.NetworkSwitchHandling)
    }

    override fun onCreateVpn() {
        vpnNetwork.addCallback(this)

        if (jniContext != 0L) {
            vpnNetwork.stop(jniContext)
            synchronized(jniLock) {
                vpnNetwork.destroy(jniContext)
                jniContext = 0
            }

        }
        jniContext = vpnNetwork.create()
    }

    override fun onStartVpn(tunfd: ParcelFileDescriptor) {
        startNative(tunfd.fd)
    }

    override fun onStopVpn() {
        stopNative()
    }

    override fun onDestroyVpn() {
        synchronized(jniLock) {
            vpnNetwork.destroy(jniContext)
            jniContext = 0
        }
        vpnNetwork.addCallback(null)
    }

    override fun mtu(): Int {
        return vpnNetwork.mtu()
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

    private fun startNative(tunfd: Int) {

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
    }

    private fun stopNative() {
        Timber.d("Stop native runtime")

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
    }
}
