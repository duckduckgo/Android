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
import androidx.annotation.VisibleForTesting
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
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.lang.IllegalStateException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val LRU_CACHE_SIZE = 2048
private const val EMFILE_ERRNO = 24
private const val TUNNEL_THREAD_JOIN_SLICE_MILLIS = 5_000L
private const val TUNNEL_THREAD_JOIN_BOUND_MILLIS = 15_000L

/**
 * The tunnel thread did not exit within the join bound after being signalled to stop.
 * Its native context is leaked and detached, never freed: freeing it under the running thread would crash.
 */
internal class TunnelThreadStopTimeoutException(message: String) : IllegalStateException(message)

@ContributesIntoSet(
    scope = VpnScope::class,
    binding = binding<VpnNetworkStack>(),
)
@ContributesBinding(
    scope = VpnScope::class,
    binding = binding<VpnNetworkCallback>(),
)
@SingleIn(VpnScope::class)
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

    // jniContext and tunnelThread are one piece of state: always read and written together under jniLock
    private val jniLock = Any()
    private var jniContext = 0L
    private var tunnelThread: Thread? = null

    @VisibleForTesting
    internal var tunnelThreadJoinBoundMillis = TUNNEL_THREAD_JOIN_BOUND_MILLIS
    private val addressLookupLruCache = LruCache<String, String>(LRU_CACHE_SIZE)

    override val name: String = AppTpVpnFeature.APPTP_VPN.featureName

    override fun onCreateVpn(): Result<Unit> {
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }

        vpnNetwork.addCallback(this)

        destroyNative(vpnNetwork).onFailure { return Result.failure(it) }
        val context = vpnNetwork.create()
        synchronized(jniLock) { jniContext = context }

        return Result.success(Unit)
    }

    override suspend fun onPrepareVpn(): Result<VpnTunnelConfig> {
        return Result.success(
            VpnTunnelConfig(
                mtu = vpnNetwork.value.mtu(),
                addresses = mapOf(
                    InetAddress.getByName("10.0.0.2") to 32,
                    InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
                ),
                dns = emptySet(),
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
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }
        return stopAndJoinTunnel(vpnNetwork)
    }

    override fun onDestroyVpn(): Result<Unit> {
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }

        // On failure the tunnel thread may still be running and calling back into us, so keep the callback registered
        destroyNative(vpnNetwork).onFailure { return Result.failure(it) }
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
        val vpnNetwork = vpnNetwork.safeGet().getOrElse { return Result.failure(it) }

        synchronized(jniLock) {
            val context = jniContext
            if (context == 0L) {
                logcat(ERROR) { "Trying to start VPN Network without previously creating it" }
                return Result.failure(IllegalStateException("Trying to start VPN Network without previously creating it"))
            }
            if (tunnelThread != null) {
                logcat(ERROR) { "Trying to start VPN Network while a tunnel thread is still running" }
                return Result.failure(IllegalStateException("Trying to start VPN Network while a tunnel thread is still running"))
            }

            logcat { "Start native runtime" }
            val level = if (appBuildConfig.isDebug || appTpLocalFeature.verboseLogging().isEnabled()) VpnNetworkLog.DEBUG else VpnNetworkLog.ASSERT
            vpnNetwork.start(context, level)

            tunnelThread = Thread {
                logcat { "Running tunnel in context $context" }
                try {
                    vpnNetwork.run(context, tunfd)
                } catch (t: Throwable) {
                    logcat(ERROR) { "Tunnel thread crashed: ${t.asLog()}" }
                    deviceShieldPixels.reportTunnelThreadAbnormalCrash()
                } finally {
                    // a late exit, e.g. of a detached thread, must not clear the reference to a newer tunnel thread
                    synchronized(jniLock) {
                        if (tunnelThread === Thread.currentThread()) tunnelThread = null
                    }
                    logcat(WARN) { "Tunnel exited" }
                }
            }.also { it.start() }
        }

        logcat { "Started tunnel thread" }
        return Result.success(Unit)
    }

    /**
     * Signals the native layer to stop and waits up to [tunnelThreadJoinBoundMillis] for the tunnel thread to exit.
     * jniLock is not held while joining because the exiting thread needs it in its finally block.
     *
     * If the thread outlives the bound, its context is leaked and detached, never freed: jniContext and tunnelThread are
     * cleared so later calls don't join it again and a new context can be created and started.
     */
    private fun stopAndJoinTunnel(vpnNetwork: VpnNetwork): Result<Unit> {
        val (context, thread) = synchronized(jniLock) { jniContext to tunnelThread }
        if (context == 0L) return Result.success(Unit)

        logcat { "Stop native runtime" }
        // if the jniContext is invalid the stop() call may fail, we log and still wait for the thread
        runCatching { vpnNetwork.stop(context) }.onFailure {
            logcat(ERROR) { "Error stopping the VPN network ${it.asLog()}" }
        }
        if (thread == null) return Result.success(Unit)

        logcat { "Joining tunnel thread context $context" }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(tunnelThreadJoinBoundMillis)
        var interrupted = false
        while (thread.isAlive) {
            val remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (remainingMillis <= 0) break
            try {
                thread.join(minOf(TUNNEL_THREAD_JOIN_SLICE_MILLIS, remainingMillis))
                if (thread.isAlive) {
                    logcat { "Timed out waiting for tunnel thread" }
                    deviceShieldPixels.reportTunnelThreadStopTimeout()
                }
            } catch (e: InterruptedException) {
                logcat { "Interrupted while waiting" }
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()

        if (thread.isAlive) {
            synchronized(jniLock) {
                if (tunnelThread === thread) {
                    jniContext = 0
                    tunnelThread = null
                }
            }
            logcat(ERROR) { "Tunnel thread still alive after ${tunnelThreadJoinBoundMillis}ms, leaked and detached context $context" }
            deviceShieldPixels.reportTunnelThreadJoinBoundExceeded()
            return Result.failure(TunnelThreadStopTimeoutException("Tunnel thread did not exit within ${tunnelThreadJoinBoundMillis}ms"))
        }

        logcat { "Stopped tunnel thread" }
        return Result.success(Unit)
    }

    /**
     * Stops and joins the tunnel thread, then frees the native context. The context is only freed once no tunnel thread runs on it.
     */
    private fun destroyNative(vpnNetwork: VpnNetwork): Result<Unit> {
        stopAndJoinTunnel(vpnNetwork).onFailure { return Result.failure(it) }

        synchronized(jniLock) {
            if (tunnelThread != null) {
                return Result.failure(IllegalStateException("Tunnel thread started while destroying the VPN network"))
            }
            if (jniContext == 0L) {
                logcat { "VPN network already destroyed...noop" }
            } else {
                vpnNetwork.destroy(jniContext)
                jniContext = 0
                logcat { "VPN network destroyed" }
            }
        }
        return Result.success(Unit)
    }

    private fun Lazy<VpnNetwork>.safeGet(): Result<VpnNetwork> {
        return runCatching {
            value
        }.onFailure {
            logcat(ERROR) { it.asLog() }
            Result.failure<VpnNetwork>(it)
        }
    }
}
