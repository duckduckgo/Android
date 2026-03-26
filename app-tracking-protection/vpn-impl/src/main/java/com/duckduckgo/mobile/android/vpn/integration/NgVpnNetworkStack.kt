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

import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackerDetector
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.feature.AppTpLocalFeature
import com.duckduckgo.mobile.android.vpn.network.DnsProvider
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.vpn.network.api.*
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.lang.IllegalStateException
import java.net.InetAddress
import javax.inject.Inject

private const val LRU_CACHE_SIZE = 2048
private const val EMFILE_ERRNO = 24

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnNetworkStack::class,
)
@ContributesBinding(
    scope = VpnScope::class,
    boundType = VpnNetworkCallback::class,
)
@SingleInstanceIn(VpnScope::class)
class NgVpnNetworkStack @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val vpnNetwork: Lazy<VpnNetwork>,
    private val runtime: Runtime,
    private val appTrackerDetector: AppTrackerDetector,
    private val trackingProtectionAppsRepository: TrackingProtectionAppsRepository,
    private val appTpLocalFeature: AppTpLocalFeature,
    private val deviceShieldPixels: DeviceShieldPixels,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val dnsProvider: DnsProvider,
) : VpnNetworkStack, VpnNetworkCallback {

    private var tunnelThread: Thread? = null
    private var jniContext = 0L
    private val jniLock = Any()
    private val addressLookupLruCache = LruCache<String, String>(LRU_CACHE_SIZE)

    override val name: String = AppTpVpnFeature.APPTP_VPN.featureName

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

    override suspend fun onPrepareVpn(): Result<VpnTunnelConfig> {
        fun getDns(): Set<InetAddress> {
            val privateDns = dnsProvider.getPrivateDns()
            if (privateDns.isNotEmpty()) {
                // when private DNS is defined we don't want to define any DNS
                return emptySet()
            }

            val targetModels = listOf(
                "moto g play",
                "moto g stylus 5G",
                "moto g(60)",
                "moto g(7) power",
                "FIG-LX1",
                "moto g 5G",
                "moto g pure",
                "moto g power",
            )
            val model = appBuildConfig.model
            if (targetModels.any { model.lowercase().contains(it.lowercase()) }) {
                // else return default system dns
                return dnsProvider.getSystemDns().toSet()
            }

            return emptySet()
        }

        return Result.success(
            VpnTunnelConfig(
                mtu = vpnNetwork.get().mtu(),
                addresses = mapOf(
                    InetAddress.getByName("10.0.0.2") to 32,
                    InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
                ),
                dns = getDns(),
                searchDomains = dnsProvider.getSearchDomains(),
                customDns = emptySet(),
                routes = emptyMap(),
                appExclusionList = trackingProtectionAppsRepository.getExclusionAppsList().toSet(),
            ),
        )
    }

    override fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit> {
        return startNative(tunfd.fd)
    }

    override fun onStopVpn(reason: VpnStopReason): Result<Unit> {
        return stopNative()
    }

    override fun onDestroyVpn(): Result<Unit> {
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }

        if (jniContext != 0L) {
            synchronized(jniLock) {
                vpnNetwork.destroy(jniContext)
                jniContext = 0
                logcat { "VPN network destroyed" }
            }
        } else {
            logcat { "VPN network already destroyed...noop" }
        }
        vpnNetwork.addCallback(null)

        return Result.success(Unit)
    }

    override fun onExit(reason: String) {
        logcat(WARN) { "Native exit reason=$reason" }

        fun killProcess() {
            runtime.exit(0)
        }
        // restart the VPN. We kill process to also avoid any memory leak in the native layer
        killProcess()
    }

    override fun onError(
        errorCode: Int,
        message: String,
    ) {
        fun Int.isEmfile(): Boolean {
            return this == EMFILE_ERRNO
        }

        logcat(WARN) { "onError $errorCode:$message" }

        if (errorCode.isEmfile()) {
            onExit(message)
        }
    }

    override fun onDnsResolved(dnsRR: DnsRR) {
        addressLookupLruCache.put(dnsRR.resource, dnsRR.qName)
        logcat { "dnsResolved called for $dnsRR" }
    }

    override fun isDomainBlocked(domainRR: DomainRR): Boolean {
        logcat { "isDomainBlocked for $domainRR" }
        return !shouldAllowDomain(domainRR.name, domainRR.uid)
    }

    override fun reportTLSParsingError(errorCode: Int) {
        logcat { "reportTLSParsingError called with errorCode: $errorCode" }
        coroutineScope.launch(dispatcherProvider.io()) {
            deviceShieldPixels.reportTLSParsingError(errorCode)
        }
    }

    override fun isAddressBlocked(addressRR: AddressRR): Boolean {
        // never blocked based on address because the different domains can point to the same IP address
        return false
    }

    private fun shouldAllowDomain(
        name: String,
        uid: Int,
    ): Boolean {
        val tracker = appTrackerDetector.evaluate(name, uid)
        logcat { "shouldAllowDomain for $name ($uid) = $tracker" }

        return tracker == null
    }

    private fun startNative(tunfd: Int): Result<Unit> {
        if (jniContext == 0L) {
            logcat(ERROR) { "Trying to start VPN Network without previously creating it" }
            return Result.failure(IllegalStateException("Trying to start VPN Network without previously creating it"))
        }

        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }
        if (tunnelThread == null) {
            logcat { "Start native runtime" }
            val level = if (appBuildConfig.isDebug || appTpLocalFeature.verboseLogging().isEnabled()) VpnNetworkLog.DEBUG else VpnNetworkLog.ASSERT

            vpnNetwork.start(jniContext, level)

            tunnelThread = Thread {
                logcat { "Running tunnel in context $jniContext" }
                try {
                    vpnNetwork.run(jniContext, tunfd)
                } catch (t: Throwable) {
                    logcat(ERROR) { "Tunnel thread crashed: ${t.asLog()}" }
                    deviceShieldPixels.reportTunnelThreadAbnormalCrash()
                } finally {
                    tunnelThread = null
                    logcat(WARN) { "Tunnel exited" }
                }
            }.also { it.start() }

            logcat { "Started tunnel thread" }
        }

        return Result.success(Unit)
    }

    private fun stopNative(): Result<Unit> {
        logcat { "Stop native runtime" }
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }

        tunnelThread?.let {
            logcat { "Stopping tunnel thread" }

            // In this case we don't check for jniContext == 0 and rather runCatching because we want to make sure we stop the tunnelThread
            // if the jniContext is invalid the stop() call should fail, we log and continue
            runCatching { vpnNetwork.stop(jniContext) }.onFailure {
                logcat(ERROR) { "Error stopping the VPN network ${it.asLog()}" }
            }

            var thread = tunnelThread
            while (thread != null && thread.isAlive) {
                try {
                    logcat { "Joining tunnel thread context $jniContext" }
                    thread.join(5000)

                    // Check if we timed out and are stuck
                    if (thread.isAlive) {
                        logcat { "Timed out waiting for tunnel thread" }
                        deviceShieldPixels.reportTunnelThreadStopTimeout()
                    }
                } catch (t: InterruptedException) {
                    logcat { "Interrupted while waiting" }
                }
                thread = tunnelThread
            }
            tunnelThread = null

            logcat { "Stopped tunnel thread" }
        }

        return Result.success(Unit)
    }

    private fun Lazy<VpnNetwork>.safeGet(): Result<VpnNetwork> {
        return runCatching {
            get()
        }.onFailure {
            logcat(ERROR) { it.asLog() }
            Result.failure<VpnNetwork>(it)
        }
    }
}
