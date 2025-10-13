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

package com.duckduckgo.mobile.android.vpn.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.system.OsConstants.AF_INET6
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.anrs.api.CrashLogger.Crash
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.utils.checkMainThread
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.library.loader.LibraryLoader
import com.duckduckgo.mobile.android.vpn.dao.VpnServiceStateStatsDao
import com.duckduckgo.mobile.android.vpn.feature.AppTpRemoteFeatures
import com.duckduckgo.mobile.android.vpn.integration.NgVpnNetworkStack
import com.duckduckgo.mobile.android.vpn.integration.VpnNetworkStackProvider
import com.duckduckgo.mobile.android.vpn.model.AlwaysOnState
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState.ENABLED
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState.ENABLING
import com.duckduckgo.mobile.android.vpn.model.VpnServiceStateStats
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.mobile.android.vpn.network.util.asRoute
import com.duckduckgo.mobile.android.vpn.network.util.getUnderlyingNetworks
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.state.VpnStateMonitorService
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.ui.notification.VpnEnabledNotificationBuilder
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlin.system.exitProcess

@InjectWith(
    scope = VpnScope::class,
    delayGeneration = true,
)
class TrackerBlockingVpnService : VpnService(), CoroutineScope by MainScope(), VpnSocketProtector {

    private external fun jni_wait_for_tun_up(tunFd: Int): Int

    private fun ParcelFileDescriptor.waitForTunnelUpOrTimeout(): Boolean {
        return runCatching {
            jni_wait_for_tun_up(this.fd) == 0
        }.getOrElse { e ->
            if (e is UnsatisfiedLinkError) {
                logcat(ERROR) { "VPN log: ${e.asLog()}" }
                // A previous error unloaded the libraries, reload them
                try {
                    logcat { "VPN log: Loading native VPN networking library" }
                    LibraryLoader.loadLibrary(this@TrackerBlockingVpnService, "netguard")
                } catch (ignored: Throwable) {
                    logcat(ERROR) { "VPN log: Error loading netguard library: ${ignored.asLog()}" }
                    exitProcess(1)
                }
            }
            Thread.sleep(100)
            true
        }
    }

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var vpnServiceCallbacksPluginPoint: PluginPoint<VpnServiceCallbacks>

    @Inject
    lateinit var memoryCollectorPluginPoint: PluginPoint<VpnMemoryCollectorPlugin>

    @Inject
    lateinit var vpnEnabledNotificationContentPluginPoint: PluginPoint<VpnEnabledNotificationContentPlugin>

    private var activeTun by Delegates.observable<ParcelFileDescriptor?>(null) { _, oldTun, newTun ->
        fun ParcelFileDescriptor?.safeFd(): Int? {
            return runCatching { this?.fd }.getOrNull()
        }
        runCatching {
            logcat { "VPN log: New tun ${newTun?.safeFd()}" }
            logcat { "VPN log: Closing old tun ${oldTun?.safeFd()}" }
            oldTun?.close()
        }.onFailure {
            logcat(ERROR) { "VPN log: Error closing old tun ${oldTun?.safeFd()}" }
        }
    }

    private val binder: VpnServiceBinder = VpnServiceBinder()

    private var vpnStateServiceReference: IBinder? = null

    @Inject lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var vpnNetworkStackProvider: VpnNetworkStackProvider

    @Inject lateinit var vpnServiceStateStatsDao: VpnServiceStateStatsDao

    @Inject lateinit var crashLogger: CrashLogger

    @Inject lateinit var dnsChangeCallback: DnsChangeCallback

    @Inject lateinit var appTpRemoteFeatures: AppTpRemoteFeatures

    private val serviceDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var vpnNetworkStack: VpnNetworkStack by VpnNetworkStackDelegate(
        provider = {
            runBlocking { vpnNetworkStackProvider.provideNetworkStack() }
        },
    )

    private val vpnStateServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?,
        ) {
            logcat { "Connected to state monitor service" }
            vpnStateServiceReference = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logcat { "Disconnected from state monitor service" }
            vpnStateServiceReference = null
        }
    }

    inner class VpnServiceBinder : Binder() {

        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int,
        ): Boolean {
            if (code == LAST_CALL_TRANSACTION) {
                onRevoke()
                return true
            }
            return false
        }

        fun getService(): TrackerBlockingVpnService {
            return this@TrackerBlockingVpnService
        }
    }

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)

        logcat { "VPN log: onCreate, creating the ${vpnNetworkStack.name} network stack" }
        vpnNetworkStack.onCreateVpnWithErrorReporting()
    }

    override fun onBind(intent: Intent?): IBinder {
        logcat { "VPN log: onBind invoked" }
        return binder
    }

    override fun onUnbind(p0: Intent?): Boolean {
        logcat { "VPN log: onUnbind invoked" }
        return super.onUnbind(p0)
    }

    override fun onDestroy() {
        logcat { "VPN log: onDestroy" }
        vpnNetworkStack.onDestroyVpn()
        super.onDestroy()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        fun Intent?.alwaysOnTriggered(): Boolean {
            return runCatching {
                (this == null || this.component == null || this.component!!.packageName != packageName)
            }.getOrElse { false }
        }

        logcat { "VPN log: onStartCommand: ${intent?.action}" }

        when (val action = intent?.action) {
            null, ACTION_START_VPN, ACTION_ALWAYS_ON_START -> {
                if (notifyVpnStart()) {
                    synchronized(this) {
                        launch(serviceDispatcher) {
                            // Give Android a moment to complete foreground transition
                            // You might think this is a hack (and it kind of is)
                            // but it's a workaround to avoid early establish() binding failures
                            delay(100)
                            async {
                                startVpn(intent.alwaysOnTriggered())
                            }.await()
                        }
                    }
                } else {
                    logcat(ERROR) { "notifyStart return error, aborting" }
                    deviceShieldPixels.notifyStartFailed()
                    // remove the notification and stop service
                    stopForeground(true)
                    stopSelf()
                }
            }
            ACTION_STOP_VPN, ACTION_SNOOZE_VPN -> {
                synchronized(this) {
                    launch(serviceDispatcher) {
                        async {
                            val snoozeTriggerAtMillisExtra = intent.getLongExtra(ACTION_SNOOZE_VPN_EXTRA, 0L)
                            stopVpn(VpnStopReason.SELF_STOP(snoozeTriggerAtMillisExtra))
                        }.await()
                    }
                }
            }
            ACTION_RESTART_VPN -> {
                synchronized(this) {
                    launch(serviceDispatcher) {
                        async {
                            startVpn()
                        }.await()
                    }
                }
            }
            else -> logcat(ERROR) { "Unknown intent action: $action" }
        }

        return Service.START_STICKY
    }

    internal fun configureUnderlyingNetworks() {
        val networks = getUnderlyingNetworks()
        if (networks.isNotEmpty()) {
            logcat { "VPN log: setting underlying WIFI/mobile networks $networks" }
            setUnderlyingNetworks(networks.toTypedArray())
        }
    }

    private suspend fun startVpn(isAlwaysOnTriggered: Boolean = false) = withContext(serviceDispatcher) {
        suspend fun updateNetworkStackUponRestart() {
            logcat { "VPN log: updating the networking stack" }
            logcat { "VPN log: CURRENT network ${vpnNetworkStack.name}" }
            // stop the current networking stack
            vpnNetworkStack.onStopVpn(VpnStopReason.RESTART)
            vpnNetworkStack.onDestroyVpn()
            // maybe we have changed the networking stack
            vpnNetworkStack = vpnNetworkStackProvider.provideNetworkStack()
            vpnNetworkStack.onCreateVpnWithErrorReporting()
            logcat { "VPN log: NEW network ${vpnNetworkStack.name}" }
        }
        deviceShieldPixels.reportVpnStartAttempt()
        dnsChangeCallback.unregister()

        runCatching {
            vpnServiceStateStatsDao.insert(createVpnState(state = ENABLING))
        }.onFailure {
            stopVpn(VpnStopReason.ERROR, false)
        }

        logcat { "VPN log: Starting VPN" }
        val restarting = activeTun != null

        if (!restarting) {
            vpnServiceCallbacksPluginPoint.getPlugins().forEach {
                logcat { "VPN log: onVpnStarting ${it.javaClass} callback" }
                it.onVpnStarting(this)
            }
        } else {
            logcat { "VPN log: skipping service callbacks while restarting" }
        }

        // Create a null route tunnel so that leaks can't scape
        val nullTun = createNullRouteTempTunnel()?.let {
            if (!it.waitForTunnelUpOrTimeout()) {
                logcat(WARN) { "VPN log: timeout waiting for null tunnel to go up" }
                null
            } else {
                it
            }
        }
        if (nullTun == null) {
            logcat(ERROR) { "VPN log: Failed to establish the null TUN interface" }
            deviceShieldPixels.vpnEstablishNullTunInterfaceError()
            stopVpn(VpnStopReason.ERROR, false)
            return@withContext
        }

        activeTun?.let {
            logcat { "VPN log: restarting the tunnel" }
            updateNetworkStackUponRestart()
            it
        }
        activeTun = nullTun

        val tunnelConfig = vpnNetworkStack.onPrepareVpn().getOrNull().also {
            if (it != null) {
                activeTun = createTunnelInterface(it)
                activeTun?.let { tun ->
                    if (!tun.waitForTunnelUpOrTimeout()) {
                        activeTun = null
                    }
                }
            } else {
                logcat(ERROR) { "VPN log: Failed to obtain config needed to establish the TUN interface" }
                stopVpn(VpnStopReason.ERROR, false)
                return@withContext
            }
        }

        if (activeTun == null) {
            logcat(ERROR) { "VPN log: Failed to establish the TUN interface" }
            deviceShieldPixels.vpnEstablishTunInterfaceError()
            stopVpn(VpnStopReason.ERROR, false)
            return@withContext
        }

        // set underlying networks
        // configureUnderlyingNetworks()

        logcat { "VPN log: Enable new error handling for onStartVpn" }
        vpnNetworkStack.onStartVpn(activeTun!!).getOrElse {
            logcat(ERROR) { "VPN log: Failed to start VPN" }
            stopVpn(VpnStopReason.ERROR, false)
            return@withContext
        }

        vpnServiceCallbacksPluginPoint.getPlugins().forEach {
            if (restarting) {
                logcat { "VPN log: onVpnReconfigured ${it.javaClass} callback" }
                it.onVpnReconfigured(this)
            } else {
                logcat { "VPN log: onVpnStarted ${it.javaClass} callback" }
                it.onVpnStarted(this)
            }
        }

        Intent(applicationContext, VpnStateMonitorService::class.java).also {
            bindService(it, vpnStateServiceConnection, Context.BIND_AUTO_CREATE)
        }

        // lastly set the VPN state to enabled
        runCatching {
            vpnServiceStateStatsDao.insert(createVpnState(state = ENABLED))
        }.onFailure {
            // onVpnStarted or onVpnReconfigured already called, ie. hasVpnAlreadyStarted = true
            stopVpn(VpnStopReason.ERROR, true)
        }

        deviceShieldPixels.reportVpnStartAttemptSuccess()

        // This is something temporary while we confirm whether we're able to fix the moto g issues with appTP
        // see https://app.asana.com/0/488551667048375/1203410036713941/f for more info
        tunnelConfig?.let { config ->
            // TODO this is temporary hack until we know this approach works for moto g. If it does we'll spend time making it better/more permanent
            if (vpnNetworkStack is NgVpnNetworkStack && config.dns.isNotEmpty()) {
                // This solution is only relevant for AppTP
                // just temporary pixel to know quantify how many users would be impacted
                deviceShieldPixels.reportMotoGFix()
                dnsChangeCallback.register()
            }
        }

        reportVpnAlwaysOnState()

        if (isAlwaysOnTriggered) {
            logcat { "VPN log: VPN was always on triggered" }
            deviceShieldPixels.reportVpnAlwaysOnTriggered()
        }
    }

    private fun createNullRouteTempTunnel(): ParcelFileDescriptor? {
        checkMainThread()

        return runCatching {
            Builder().run {
                allowFamily(AF_INET6)
                try {
                    addAddress(InetAddress.getByName("10.0.100.100"), 32)
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to add IPv4 address 10.0.100.100/32", e)
                }
                try {
                    addAddress(InetAddress.getByName("fd00::1"), 128)
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to add IPv6 address fd00::1/128", e)
                }
                // nobody will be listening here we just want to make sure no app has connection
                try {
                    addDnsServer("10.0.100.1")
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to add DNS server 10.0.100.1", e)
                }
                // just so that we can connect to our BE
                // TODO should we protect all comms with our controller BE? other VPNs do that
                safelyAddDisallowedApps(listOf(this@TrackerBlockingVpnService.packageName))
                setBlocking(true)
                setMtu(1280)
                try {
                    prepare(this@TrackerBlockingVpnService)
                } catch (e: Exception) {
                    throw IllegalStateException("VPN service not prepared", e)
                }
                establish()
            }.also {
                logcat { "VPN log: Hole TUN created ${it?.fd}" }
            }
        }.onFailure {
            // We still one to log this instance to be able to fix it
            crashLogger.logCrash(Crash("vpn_create_null_tunnel", it))
        }.getOrNull()
    }

    private suspend fun createTunnelInterface(
        tunnelConfig: VpnTunnelConfig,
    ): ParcelFileDescriptor? {
        val tunInterface = Builder().run {
            tunnelConfig.addresses.forEach { addAddress(it.key, it.value) }
            val tunHasIpv6Address = tunnelConfig.addresses.any { it.key is Inet6Address }

            // Allow IPv6 to go through the VPN
            // See https://developer.android.com/reference/android/net/VpnService.Builder#allowFamily(int) for more info as to why
            if (tunHasIpv6Address) {
                logcat { "VPN log: Allowing IPv6 traffic through the tun interface" }
                allowFamily(AF_INET6)
            }

            val tunnelDns = checkAndReturnDns(tunnelConfig.dns)
            val customDns = checkAndReturnDns(tunnelConfig.customDns).filterIsInstance<Inet4Address>()
            val dnsToConfigure = customDns.ifEmpty { tunnelDns }

            // TODO: eventually routes will be set by remote config
            if (appBuildConfig.isPerformanceTest && appBuildConfig.isInternalBuild()) {
                // Currently allowing host PC address 10.0.2.2 when running performance test on an emulator (normally we don't route local traffic)
                // The address is also isolated to minimize network interference during performance tests
                VpnRoutes.includedTestRoutes.forEach { addRoute(it.address, it.maskWidth) }
            } else {
                // If tunnel config has routes use them, else use the defaults.
                // we're mapping her to a list of pairs because we want to make sure that when we combine them with other defaults, eg. add the DNS
                // addresses, we don't override any entry in the map
                val vpnRoutes = tunnelConfig.routes
                    .map { it.key to it.value }
                    .ifEmpty { VpnRoutes.includedRoutes.asAddressMaskPair() }.toMutableList()

                // Any DNS shall be added to the routes, to ensure its traffic goes through the VPN, specifically because we can have local DNSes
                // TODO filtering Ipv6 out for now for simplicity. Once we support IPv6 we'll come back to this
                dnsToConfigure.filterIsInstance<Inet4Address>().forEach { dns ->
                    dns.asRoute()?.let {
                        logcat { "VPN log: Adding tunnel config DNS address $it to VPN routes" }
                        vpnRoutes.add(it.address to it.maskWidth)
                    }
                }

                vpnRoutes.mapNotNull { runCatching { InetAddress.getByName(it.first) to it.second }.getOrNull() }
                    .filter { (it.first is Inet4Address) || (tunHasIpv6Address && it.first is Inet6Address) }
                    .forEach { route ->
                        if (!route.first.isLoopbackAddress) {
                            logcat { "Adding route $route" }
                            runCatching {
                                addRoute(route.first, route.second)
                            }.onFailure {
                                logcat(WARN) { "VPN log: Error setting route $route: ${it.asLog()}" }
                            }
                        } else {
                            logcat(WARN) { "VPN log: Tried to add loopback address $route to VPN routes" }
                        }
                    }
            }

            // Add the route for all Global Unicast Addresses. This is the IPv6 equivalent to
            // IPv4 public IP addresses. They are addresses that routable in the internet
            if (tunHasIpv6Address) {
                logcat { "VPN log: Setting IPv6 address in the tun interface" }
                addRoute("2000::", 3)
            }

            setBlocking(true)
            // Cap the max MTU value to avoid backpressure issues in the socket
            // This is effectively capping the max segment size too
            setMtu(tunnelConfig.mtu)
            configureMeteredConnection()

            // Set DNS
            dnsToConfigure
                .filter { (it is Inet4Address) || (tunHasIpv6Address && it is Inet6Address) }
                .forEach { addr ->
                    logcat { "VPN log: Adding DNS $addr" }
                    runCatching {
                        addDnsServer(addr)
                    }.onFailure { t ->
                        logcat(ERROR) { "VPN log: Error setting DNS $addr: ${t.asLog()}" }
                        if (addr.isLoopbackAddress) {
                            deviceShieldPixels.reportLoopbackDnsError()
                        } else if (addr.isAnyLocalAddress) {
                            deviceShieldPixels.reportAnylocalDnsError()
                        } else {
                            deviceShieldPixels.reportGeneralDnsError()
                        }
                    }
                }

            logcat { "VPN log: adding search domains: ${tunnelConfig.searchDomains}" }
            if (appTpRemoteFeatures.setSearchDomains().isEnabled()) {
                tunnelConfig.searchDomains?.let {
                    addSearchDomain(it)
                }
            }

            safelyAddDisallowedApps(tunnelConfig.appExclusionList.toList())

            // Apparently we always need to call prepare, even tho not clear in docs
            // without this prepare, establish() returns null after device reboot
            prepare(this@TrackerBlockingVpnService.applicationContext)
            establish()
        }

        if (tunInterface == null) {
            logcat(ERROR) { "VPN log: Failed to establish VPN tunnel" }
        } else {
            logcat { "VPN log: Final TUN interface created ${tunInterface.fd}" }
        }

        return tunInterface
    }

    private fun checkAndReturnDns(originalDns: Set<InetAddress>): Set<InetAddress> {
        // private extension function, this is purposely here to limit visibility
        fun Set<InetAddress>.containsIpv4(): Boolean {
            forEach {
                if (it is Inet4Address) return true
            }
            return false
        }

        if (!originalDns.containsIpv4()) {
            // never allow IPv6-only DNS
            logcat(WARN) { "VPN log: No IPv4 DNS found" }
        }

        return originalDns
    }

    private fun Builder.safelyAddDisallowedApps(apps: List<String>) {
        for (app in apps) {
            try {
                logcat { "VPN log: Excluding app from VPN: $app" }
                addDisallowedApplication(app)
            } catch (e: PackageManager.NameNotFoundException) {
                logcat(WARN) { "VPN log: Package name not found: $app" }
            }
        }
    }

    private suspend fun stopVpn(
        reason: VpnStopReason,
        hasVpnAlreadyStarted: Boolean = true,
    ) = withContext(serviceDispatcher) {
        logcat { "VPN log: Stopping VPN. $reason" }

        vpnNetworkStack.onStopVpn(reason)

        activeTun = null

        sendStopPixels(reason)

        // If VPN has been started, then onVpnStopped must be called. Else, an error might have occurred before start so we call onVpnStartFailed
        if (hasVpnAlreadyStarted) {
            vpnServiceCallbacksPluginPoint.getPlugins().forEach {
                logcat { "VPN log: stopping ${it.javaClass} callback" }
                it.onVpnStopped(this, reason)
            }
        } else {
            vpnServiceCallbacksPluginPoint.getPlugins().forEach {
                logcat { "VPN log: onVpnStartFailed ${it.javaClass} callback" }
                it.onVpnStartFailed(this)
            }
        }

        // Set the state to DISABLED here, then call the on stop/failure callbacks
        runCatching {
            vpnServiceStateStatsDao.insert(createVpnState(state = VpnServiceState.DISABLED, stopReason = reason))
        }

        vpnStateServiceReference?.let {
            runCatching { unbindService(vpnStateServiceConnection).also { vpnStateServiceReference = null } }
        }

        dnsChangeCallback.unregister()

        stopForeground(true)
        stopSelf()
    }

    private fun sendStopPixels(reason: VpnStopReason) {
        when (reason) {
            is VpnStopReason.SELF_STOP, VpnStopReason.RESTART, VpnStopReason.UNKNOWN -> {} // no-op
            VpnStopReason.ERROR -> deviceShieldPixels.startError()
            VpnStopReason.REVOKED -> deviceShieldPixels.suddenKillByVpnRevoked()
        }
    }

    @Suppress("NewApi") // we use appBuildConfig
    private fun VpnService.Builder.configureMeteredConnection() {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.Q) {
            setMetered(false)
        }
    }

    override fun onRevoke() {
        logcat(WARN) { "VPN log: onRevoke called" }
        launch { stopVpn(VpnStopReason.REVOKED) }
    }

    override fun onLowMemory() {
        logcat(WARN) { "VPN log: onLowMemory called" }
    }

    // https://developer.android.com/reference/android/app/Service.html#onTrimMemory(int)
    override fun onTrimMemory(level: Int) {
        logcat { "VPN log: onTrimMemory level $level called" }

        // Collect memory data info from memory collectors
        val memoryData = mutableMapOf<String, String>()
        memoryCollectorPluginPoint.getPlugins().forEach { memoryData.putAll(it.collectMemoryMetrics()) }

        if (memoryData.isEmpty()) {
            logcat { "VPN log: nothing to send from memory collectors" }
            return
        }

        when (level) {
            TRIM_MEMORY_BACKGROUND -> deviceShieldPixels.vpnProcessExpendableLow(memoryData)
            TRIM_MEMORY_MODERATE -> deviceShieldPixels.vpnProcessExpendableModerate(memoryData)
            TRIM_MEMORY_COMPLETE -> deviceShieldPixels.vpnProcessExpendableComplete(memoryData)
            TRIM_MEMORY_RUNNING_MODERATE -> deviceShieldPixels.vpnMemoryRunningModerate(memoryData)
            TRIM_MEMORY_RUNNING_LOW -> deviceShieldPixels.vpnMemoryRunningLow(memoryData)
            TRIM_MEMORY_RUNNING_CRITICAL -> deviceShieldPixels.vpnMemoryRunningCritical(memoryData)
            else -> {} // no-op
        }
    }

    /**
     * @return `true` if the start was initiated correctly, otherwise `false`
     */
    @SuppressLint("NewApi")
    private fun notifyVpnStart(): Boolean {
        val emptyNotification = VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent.EMPTY
        var vpnNotification: VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent = emptyNotification
        for (retries in 1..20) {
            vpnNotification =
                vpnEnabledNotificationContentPluginPoint.getHighestPriorityPlugin()?.getInitialContent()
                    ?: emptyNotification

            if (vpnNotification != emptyNotification) {
                logcat { "Notification in retry: $retries" }
                break
            }
        }

        try {
            ServiceCompat.startForeground(
                this,
                VPN_FOREGROUND_SERVICE_ID,
                VpnEnabledNotificationBuilder.buildVpnEnabledNotification(applicationContext, vpnNotification),
                FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (_: Throwable) {
            // signal the error
            return false
        }

        return vpnNotification != emptyNotification
    }

    private fun reportVpnAlwaysOnState() {
        @SuppressLint("NewApi") // IDE doesn't get we use appBuildConfig
        if (appBuildConfig.sdkInt >= 29) {
            if (this@TrackerBlockingVpnService.isAlwaysOn) deviceShieldPixels.reportAlwaysOnEnabledDaily()
            if (this@TrackerBlockingVpnService.isLockdownEnabled) deviceShieldPixels.reportAlwaysOnLockdownEnabledDaily()
        }
    }

    @SuppressLint("NewApi")
    private fun createVpnState(
        state: VpnServiceState,
        stopReason: VpnStopReason = VpnStopReason.UNKNOWN,
    ): VpnServiceStateStats {
        fun VpnStopReason.asVpnStoppingReason(): VpnStoppingReason {
            return when (this) {
                VpnStopReason.RESTART -> VpnStoppingReason.RESTART
                is VpnStopReason.SELF_STOP -> VpnStoppingReason.SELF_STOP
                VpnStopReason.REVOKED -> VpnStoppingReason.REVOKED
                VpnStopReason.ERROR -> VpnStoppingReason.ERROR
                VpnStopReason.UNKNOWN -> VpnStoppingReason.UNKNOWN
            }
        }

        val isAlwaysOnEnabled = if (appBuildConfig.sdkInt >= 29) isAlwaysOn else false
        val isLockdownEnabled = if (appBuildConfig.sdkInt >= 29) isLockdownEnabled else false

        return VpnServiceStateStats(
            state = state,
            alwaysOnState = AlwaysOnState(isAlwaysOnEnabled, isLockdownEnabled),
            stopReason = stopReason.asVpnStoppingReason(),
        )
    }

    companion object {
        const val VPN_REMINDER_NOTIFICATION_ID = 999
        const val VPN_FOREGROUND_SERVICE_ID = 200

        private fun serviceIntent(context: Context): Intent {
            return Intent(context, TrackerBlockingVpnService::class.java)
        }

        private fun startIntent(context: Context): Intent {
            return serviceIntent(context).also {
                it.action = ACTION_START_VPN
            }
        }

        private fun stopIntent(context: Context): Intent {
            return serviceIntent(context).also {
                it.action = ACTION_STOP_VPN
            }
        }

        private fun restartIntent(context: Context): Intent {
            return serviceIntent(context).also {
                it.action = ACTION_RESTART_VPN
            }
        }

        private fun snoozeIntent(context: Context, triggerAtMillis: Long): Intent {
            return serviceIntent(context).also {
                it.action = ACTION_SNOOZE_VPN
                it.putExtra(ACTION_SNOOZE_VPN_EXTRA, triggerAtMillis)
            }
        }

        // This method was deprecated in API level 26. As of Build.VERSION_CODES.O,
        // this method is no longer available to third party applications.
        // For backwards compatibility, it will still return the caller's own services.
        // So for us it's still valid because we don't need to know third party services, just ours.
        @Suppress("DEPRECATION")
        internal fun isServiceRunning(context: Context): Boolean {
            val manager = kotlin.runCatching {
                context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            }.getOrElse {
                return false
            }

            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (TrackerBlockingVpnService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }

        internal fun startService(context: Context) {
            startVpnService(context.applicationContext)
        }

        internal fun snoozeService(context: Context, triggerAtMillis: Long) {
            val appContext = context.applicationContext

            if (!isServiceRunning(appContext)) return

            snoozeIntent(appContext, triggerAtMillis).run {
                appContext.startForegroundServiceWithFallback(this)
            }
        }

        // TODO commented out for now, we'll see if we need it once we enable the new networking layer
//        private fun startTrampolineService(context: Context) {
//            val applicationContext = context.applicationContext
//
//            if (isServiceRunning(applicationContext)) return
//
//            runCatching {
//                Intent(applicationContext, VpnTrampolineService::class.java).also { i ->
//                    applicationContext.startService(i)
//                }
//            }.onFailure {
//                // fallback for when both browser and vpn processes are not up, as we can't start a non-foreground service in the background
//                logcat(WARN) { "VPN log: Failed to start trampoline service: ${it.asLog()} }
//                startVpnService(applicationContext)
//            }
//        }

        internal fun startVpnService(context: Context) {
            val applicationContext = context.applicationContext

            if (isServiceRunning(applicationContext)) return

            startIntent(applicationContext).run {
                applicationContext.startForegroundServiceWithFallback(this)
            }
        }

        internal fun stopService(context: Context) {
            val applicationContext = context.applicationContext

            if (!isServiceRunning(applicationContext)) return

            stopIntent(applicationContext).run {
                applicationContext.startForegroundServiceWithFallback(this)
            }
        }

        private fun restartService(context: Context) {
            val applicationContext = context.applicationContext

            restartIntent(applicationContext).run {
                applicationContext.startForegroundServiceWithFallback(this)
            }
        }

        internal fun restartVpnService(
            context: Context,
            forceGc: Boolean = false,
            forceRestart: Boolean = false,
        ) {
            val applicationContext = context.applicationContext
            if (isServiceRunning(applicationContext)) {
                restartService(applicationContext)

                if (forceGc) {
                    logcat { "VPN log: Forcing a garbage collection to run while VPN is restarting" }
                    System.gc()
                }
            } else if (forceRestart) {
                logcat { "VPN log: starting service" }
                startVpnService(applicationContext)
            }
        }

        @SuppressLint("DenyListedApi") // static private method
        private fun Context.startForegroundServiceWithFallback(intent: Intent) {
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (ex: ForegroundServiceStartNotAllowedException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        this.startService(intent)
                    } catch (_: Throwable) {
                        // no-op
                    }
                } else {
                    throw ex
                }
            }
        }

        private const val ACTION_START_VPN = "ACTION_START_VPN"
        private const val ACTION_STOP_VPN = "ACTION_STOP_VPN"
        private const val ACTION_RESTART_VPN = "ACTION_RESTART_VPN"
        private const val ACTION_SNOOZE_VPN = "ACTION_SNOOZE_VPN"
        private const val ACTION_SNOOZE_VPN_EXTRA = "triggerAtMillis"
        private const val ACTION_ALWAYS_ON_START = "android.net.VpnService"
    }

    private fun VpnNetworkStack.onCreateVpnWithErrorReporting() {
        if (this.onCreateVpn().isFailure) {
            logcat { "VPN log: error creating the VPN network ${this.name}" }
            // report and proceed
            deviceShieldPixels.reportErrorCreatingVpnNetworkStack()
        } else {
            logcat { "VPN log: VPN network ${this.name} created" }
        }
    }
}

@Module
@ContributesTo(VpnScope::class)
abstract class VpnSocketProtectorModule {
    @Binds
    abstract fun bindVpnSocketProtector(impl: TrackerBlockingVpnService): VpnSocketProtector
}
