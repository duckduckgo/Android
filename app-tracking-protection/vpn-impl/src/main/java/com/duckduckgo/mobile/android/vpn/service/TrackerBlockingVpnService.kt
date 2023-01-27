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
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.*
import android.system.OsConstants.AF_INET6
import androidx.core.content.ContextCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.extensions.getPrivateDnsServerName
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.dao.VpnServiceStateStatsDao
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.integration.VpnNetworkStackProvider
import com.duckduckgo.mobile.android.vpn.model.AlwaysOnState
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState.ENABLED
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState.ENABLING
import com.duckduckgo.mobile.android.vpn.model.VpnServiceStateStats
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.mobile.android.vpn.network.util.asRoute
import com.duckduckgo.mobile.android.vpn.network.util.getActiveNetwork
import com.duckduckgo.mobile.android.vpn.network.util.getSystemActiveNetworkDefaultDns
import com.duckduckgo.mobile.android.vpn.network.util.isLocal
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.prefs.VpnPreferences
import com.duckduckgo.mobile.android.vpn.service.state.VpnStateMonitorService
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.ui.notification.VpnEnabledNotificationBuilder
import dagger.android.AndroidInjection
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.*
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

@Suppress("NoHardcodedCoroutineDispatcher")
@InjectWith(VpnScope::class)
class TrackerBlockingVpnService : VpnService(), CoroutineScope by MainScope() {

    @Inject
    lateinit var vpnPreferences: VpnPreferences

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var vpnServiceCallbacksPluginPoint: PluginPoint<VpnServiceCallbacks>

    @Inject
    lateinit var memoryCollectorPluginPoint: PluginPoint<VpnMemoryCollectorPlugin>

    @Inject
    lateinit var vpnEnabledNotificationContentPluginPoint: PluginPoint<VpnEnabledNotificationContentPlugin>

    private var tunInterface: ParcelFileDescriptor? = null

    private val binder: VpnServiceBinder = VpnServiceBinder()

    private var vpnStateServiceReference: IBinder? = null

    @Inject lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var appTpFeatureConfig: AppTpFeatureConfig

    @Inject lateinit var vpnNetworkStackProvider: VpnNetworkStackProvider

    @Inject lateinit var vpnServiceStateStatsDao: VpnServiceStateStatsDao

    private var restartRequested = false

    private val startVpnLock = Object()

    private val alwaysOnStateJob = ConflatedJob()

    private val isInterceptDnsTrafficEnabled by lazy {
        appTpFeatureConfig.isEnabled(AppTpSetting.InterceptDnsTraffic)
    }

    private val isIpv6SupportEnabled by lazy {
        appTpFeatureConfig.isEnabled(AppTpSetting.Ipv6Support)
    }

    private val isPrivateDnsSupportEnabled by lazy {
        appTpFeatureConfig.isEnabled(AppTpSetting.PrivateDnsSupport)
    }

    private val isAlwaysSetDNSEnabled by lazy {
        appTpFeatureConfig.isEnabled(AppTpSetting.AlwaysSetDNS)
    }

    private val isStartVpnErrorHandlingEnabled by lazy {
        appTpFeatureConfig.isEnabled(AppTpSetting.StartVpnErrorHandling)
    }

    private val vpnNetworkStack by lazy {
        vpnNetworkStackProvider.provideNetworkStack()
    }

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

        logcat { "VPN log onCreate, creating the ${vpnNetworkStack.name} network stack" }
        vpnNetworkStack.onCreateVpn().getOrNull()?.let {
            // report and proceed
            deviceShieldPixels.reportErrorCreatingVpnNetworkStack()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        logcat { "VPN log onBind invoked" }
        return binder
    }

    override fun onUnbind(p0: Intent?): Boolean {
        logcat { "VPN log onUnbind invoked" }
        return super.onUnbind(p0)
    }

    override fun onDestroy() {
        logcat { "VPN log onDestroy" }
        vpnNetworkStack.onDestroyVpn()
        super.onDestroy()

        if (restartRequested) {
            restartRequested = false
            startService(this)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        logcat { "VPN log onStartCommand: ${intent?.action}" }

        var returnCode: Int = Service.START_NOT_STICKY

        restartRequested = false

        when (val action = intent?.action) {
            ACTION_START_VPN, ACTION_ALWAYS_ON_START -> {
                notifyVpnStart()
                launch { startVpn() }
                returnCode = Service.START_REDELIVER_INTENT
            }
            ACTION_STOP_VPN -> {
                launch { stopVpn(VpnStopReason.SELF_STOP) }
            }
            ACTION_RESTART_VPN -> {
                restartRequested = true
                launch { stopVpn(VpnStopReason.RESTART) }
            }
            else -> logcat(LogPriority.ERROR) { "Unknown intent action: $action" }
        }

        return returnCode
    }

    private suspend fun startVpn() = withContext(Dispatchers.IO) {
        logcat { "VPN log: Starting VPN" }

        synchronized(startVpnLock) {
            val currStateStats = vpnServiceStateStatsDao.getLastStateStats()
            // We don't check for ENABLED state here because it break when the app is updated
            // (See: https://app.asana.com/0/488551667048375/1203621692416589/f)
            if (currStateStats?.state == ENABLING) {
                // Sometimes onStartCommand gets called twice - this is a safety rail against that
                logcat(LogPriority.WARN) { "VPN is already being started, abort" }
                return@withContext
            }

            vpnServiceStateStatsDao.insert(createVpnState(state = ENABLING))

            vpnServiceCallbacksPluginPoint.getPlugins().forEach {
                logcat { "VPN log: onVpnStarting ${it.javaClass} callback" }
                it.onVpnStarting(this)
            }
        }

        vpnNetworkStack.onPrepareVpn().getOrNull().also {
            if (it != null) {
                createTunnelInterface(it)
            } else {
                logcat(LogPriority.ERROR) { "Failed to obtain config needed to establish the TUN interface" }
                stopVpn(VpnStopReason.ERROR, false)
                return@withContext
            }
        }

        if (tunInterface == null) {
            logcat(LogPriority.ERROR) { "Failed to establish the TUN interface" }
            deviceShieldPixels.vpnEstablishTunInterfaceError()
            return@withContext
        }

        if (isInterceptDnsTrafficEnabled) {
            applicationContext.getActiveNetwork()?.let { an ->
                logcat { "Setting underlying network $an" }
                setUnderlyingNetworks(arrayOf(an))
            }
        } else {
            logcat { "NetworkSwitchHandling disabled...skip setting underlying network" }
        }

        if (isStartVpnErrorHandlingEnabled) {
            logcat { "Enable new error handling for onStartVpn" }
            vpnNetworkStack.onStartVpn(tunInterface!!).getOrElse {
                logcat(LogPriority.ERROR) { "Failed to start VPN" }
                stopVpn(VpnStopReason.ERROR, false)
                return@withContext
            }
        } else {
            logcat { "Reverted error handling for onStartVpn" }
            vpnNetworkStack.onStartVpn(tunInterface!!).getOrThrow()
        }

        vpnServiceCallbacksPluginPoint.getPlugins().forEach {
            logcat { "VPN log: onVpnStarted ${it.javaClass} callback" }
            it.onVpnStarted(this)
        }

        Intent(applicationContext, VpnStateMonitorService::class.java).also {
            bindService(it, vpnStateServiceConnection, Context.BIND_AUTO_CREATE)
        }

        // lastly set the VPN state to enabled
        vpnServiceStateStatsDao.insert(createVpnState(state = ENABLED))
        alwaysOnStateJob += launch { monitorVpnAlwaysOnState() }
    }

    private suspend fun createTunnelInterface(tunnelConfig: VpnTunnelConfig) {
        tunInterface = Builder().run {
            tunnelConfig.addresses.forEach { addAddress(it.key, it.value) }

            // Allow IPv6 to go through the VPN
            // See https://developer.android.com/reference/android/net/VpnService.Builder#allowFamily(int) for more info as to why
            allowFamily(AF_INET6)

            val dnsList = getDns(tunnelConfig.dns)

            // TODO: eventually routes will be set by remote config
            if (appBuildConfig.isPerformanceTest && appBuildConfig.isInternalBuild()) {
                // Currently allowing host PC address 10.0.2.2 when running performance test on an emulator (normally we don't route local traffic)
                // The address is also isolated to minimize network interference during performance tests
                VpnRoutes.includedTestRoutes.forEach { addRoute(it.address, it.maskWidth) }
            } else {
                val vpnRoutes = VpnRoutes.includedRoutes.toMutableSet()
                if (isInterceptDnsTrafficEnabled) {
                    // we need to make sure that all DNS traffic goes through the VPN. Specifically when the DNS server is on the local network
                    dnsList.filterIsInstance<Inet4Address>().forEach { addr ->
                        addr.asRoute()?.let {
                            logcat { "Adding DNS address $it to VPN routes" }
                            vpnRoutes.add(it)
                        }
                    }
                }
                vpnRoutes.forEach { route ->
                    // convert to InetAddress to later check if it's loopback
                    kotlin.runCatching { InetAddress.getByName(route.address) }.getOrNull()?.let {
                        if (!it.isLoopbackAddress) {
                            addRoute(route.address, route.maskWidth)
                        } else {
                            logcat(LogPriority.WARN) { "Tried to add loopback address $it to VPN routes" }
                        }
                    }
                }
            }

            // Add the route for all Global Unicast Addresses. This is the IPv6 equivalent to
            // IPv4 public IP addresses. They are addresses that routable in the internet
            addRoute("2000::", 3)

            tunnelConfig.routes.forEach {
                addRoute(it.key, it.value)
            }

            setBlocking(true)
            // Cap the max MTU value to avoid backpressure issues in the socket
            // This is effectively capping the max segment size too
            setMtu(tunnelConfig.mtu)
            configureMeteredConnection()

            // Set DNS
            dnsList.forEach { addr ->
                if (isIpv6SupportEnabled || addr is Inet4Address) {
                    logcat { "Adding DNS $addr" }
                    runCatching {
                        addDnsServer(addr)
                    }.onFailure { t ->
                        logcat(LogPriority.ERROR) { "Error setting DNS $addr: ${t.asLog()}" }
                        if (addr.isLoopbackAddress) {
                            deviceShieldPixels.reportLoopbackDnsError()
                        } else if (addr.isAnyLocalAddress) {
                            deviceShieldPixels.reportAnylocalDnsError()
                        } else {
                            deviceShieldPixels.reportGeneralDnsError()
                        }
                    }
                }
            }

            safelyAddDisallowedApps(tunnelConfig.appExclusionList.toList())

            // Apparently we always need to call prepare, even tho not clear in docs
            // without this prepare, establish() returns null after device reboot
            prepare(this@TrackerBlockingVpnService.applicationContext)
            establish()
        }

        if (tunInterface == null) {
            logcat(LogPriority.ERROR) { "VPN log: Failed to establish VPN tunnel" }
            stopVpn(VpnStopReason.ERROR, false)
        }
    }

    private fun getDns(configDns: Set<InetAddress>): Set<InetAddress> {
        // private extension function, this is purposely here to limit visibility
        fun Set<InetAddress>.containsIpv4(): Boolean {
            forEach {
                if (it is Inet4Address) return true
            }
            return false
        }

        val dns = mutableSetOf<InetAddress>()

        // Add DNS specific to VPNetworkStack
        configDns.forEach { dns.add(it) }

        // System DNS
        if (isInterceptDnsTrafficEnabled) {
            kotlin.runCatching {
                applicationContext.getSystemActiveNetworkDefaultDns()
                    .map { InetAddress.getByName(it) }
            }.getOrNull()?.run {
                for (inetAddress in this) {
                    if (!dns.contains(inetAddress) && !(inetAddress.isLocal())) {
                        dns.add(inetAddress)
                    }
                }
            }
        }

        // Android Private DNS (added by the user)
        if (isPrivateDnsSupportEnabled && vpnPreferences.isPrivateDnsEnabled) {
            runCatching {
                InetAddress.getAllByName(applicationContext.getPrivateDnsServerName())
            }.getOrNull()?.run { dns.addAll(this) }
        }

        // This is purely internal, never to go to production
        if (appBuildConfig.isInternalBuild() && isAlwaysSetDNSEnabled) {
            if (dns.isEmpty()) {
                kotlin.runCatching {
                    logcat { "Adding cloudflare DNS" }
                    dns.add(InetAddress.getByName("1.1.1.1"))
                    dns.add(InetAddress.getByName("1.0.0.1"))
                    if (isIpv6SupportEnabled) {
                        dns.add(InetAddress.getByName("2606:4700:4700::1111"))
                        dns.add(InetAddress.getByName("2606:4700:4700::1001"))
                    }
                }.onFailure {
                    logcat(LogPriority.WARN) { "Error adding fallback DNS: ${it.asLog()}" }
                }
            }

            // always add ipv4 DNS
            if (!dns.containsIpv4()) {
                logcat { "DNS set does not contain IPv4, adding cloudflare" }
                kotlin.runCatching {
                    dns.add(InetAddress.getByName("1.1.1.1"))
                    dns.add(InetAddress.getByName("1.0.0.1"))
                }.onFailure {
                    logcat(LogPriority.WARN) { "Error adding fallback DNS ${it.asLog()}" }
                }
            }
        }

        if (!dns.containsIpv4()) {
            // never allow IPv6-only DNS
            logcat { "No IPv4 DNS found, return empty DNS list" }
            return setOf()
        }

        return dns.toSet()
    }

    private fun Builder.safelyAddDisallowedApps(apps: List<String>) {
        for (app in apps) {
            try {
                logcat { "Excluding app from VPN: $app" }
                addDisallowedApplication(app)
            } catch (e: PackageManager.NameNotFoundException) {
                logcat(LogPriority.WARN) { "Package name not found: $app" }
            }
        }
    }

    private suspend fun stopVpn(
        reason: VpnStopReason,
        hasVpnAlreadyStarted: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        logcat { "VPN log: Stopping VPN. $reason" }

        vpnNetworkStack.onStopVpn(reason)

        tunInterface?.close()
        tunInterface = null

        alwaysOnStateJob.cancel()

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

        vpnStateServiceReference?.let {
            runCatching { unbindService(vpnStateServiceConnection).also { vpnStateServiceReference = null } }
        }

        // Set the state to DISABLED here, then call the on stop/failure callbacks
        vpnServiceStateStatsDao.insert(createVpnState(state = VpnServiceState.DISABLED, stopReason = reason))

        stopForeground(true)
        stopSelf()
    }

    private fun sendStopPixels(reason: VpnStopReason) {
        when (reason) {
            VpnStopReason.SELF_STOP, VpnStopReason.RESTART, VpnStopReason.UNKNOWN -> {} // no-op
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
        logcat(LogPriority.WARN) { "VPN log onRevoke called" }
        launch { stopVpn(VpnStopReason.REVOKED) }
    }

    override fun onLowMemory() {
        logcat(LogPriority.WARN) { "VPN log onLowMemory called" }
    }

    // https://developer.android.com/reference/android/app/Service.html#onTrimMemory(int)
    override fun onTrimMemory(level: Int) {
        logcat { "VPN log onTrimMemory level $level called" }

        // Collect memory data info from memory collectors
        val memoryData = mutableMapOf<String, String>()
        memoryCollectorPluginPoint.getPlugins().forEach { memoryData.putAll(it.collectMemoryMetrics()) }

        if (memoryData.isEmpty()) {
            logcat { "VPN log nothing to send from memory collectors" }
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

    private fun notifyVpnStart() {
        val content = vpnEnabledNotificationContentPluginPoint.getHighestPriorityPlugin().getInitialContent()
        val vpnNotification = content ?: VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent.EMPTY

        startForeground(
            VPN_FOREGROUND_SERVICE_ID,
            VpnEnabledNotificationBuilder.buildVpnEnabledNotification(applicationContext, vpnNotification),
        )
    }

    private suspend fun monitorVpnAlwaysOnState() = withContext(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
        suspend fun incrementalPeriodicChecks(
            times: Int = Int.MAX_VALUE,
            initialDelay: Long = 500, // 0.5 second
            maxDelay: Long = 300_000, // 5 minutes
            factor: Double = 1.05, // 5% increase
            block: suspend () -> Unit,
        ) {
            var currentDelay = initialDelay
            repeat(times - 1) {
                try {
                    if (isActive) block()
                } catch (t: Throwable) {
                    // you can log an error here and/or make a more finer-grained
                    // analysis of the cause to see if retry is needed
                }
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }

        val vpnState = createVpnState(ENABLED)

        @SuppressLint("NewApi") // IDE doesn't get we use appBuildConfig
        if (appBuildConfig.sdkInt >= 29) {
            incrementalPeriodicChecks {
                if (vpnServiceStateStatsDao.getLastStateStats()?.state == ENABLED) {
                    if (vpnState.alwaysOnState.alwaysOnEnabled) deviceShieldPixels.reportAlwaysOnEnabledDaily()
                    if (vpnState.alwaysOnState.alwaysOnLockedDown) deviceShieldPixels.reportAlwaysOnLockdownEnabledDaily()

                    vpnServiceStateStatsDao.insert(vpnState).also { logcat { "VPN: log, state: $vpnState" } }
                }
            }
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
                VpnStopReason.SELF_STOP -> VpnStoppingReason.SELF_STOP
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
        const val ACTION_VPN_REMINDER_RESTART = "com.duckduckgo.vpn.internaltesters.reminder.restart"

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
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                        return service.started
                    }
                    return true
                }
            }
            return false
        }

        internal fun startService(context: Context) {
            startVpnService(context.applicationContext)
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
//                Timber.w(it, "VPN log: Failed to start trampoline service")
//                startVpnService(applicationContext)
//            }
//        }

        internal fun startVpnService(context: Context) {
            val applicationContext = context.applicationContext

            if (isServiceRunning(applicationContext)) return

            startIntent(applicationContext).run {
                ContextCompat.startForegroundService(applicationContext, this)
            }
        }

        internal fun stopService(context: Context) {
            val applicationContext = context.applicationContext

            if (!isServiceRunning(applicationContext)) return

            stopIntent(applicationContext).run {
                ContextCompat.startForegroundService(applicationContext, this)
            }
        }

        private fun restartService(context: Context) {
            val applicationContext = context.applicationContext

            restartIntent(applicationContext).run {
                ContextCompat.startForegroundService(applicationContext, this)
            }
        }

        internal fun restartVpnService(
            context: Context,
            forceGc: Boolean = false,
            forceRestart: Boolean = false,
        ) {
            val applicationContext = context.applicationContext
            if (isServiceRunning(applicationContext)) {
                logcat { "VPN log: stopping service" }

                restartService(applicationContext)

                if (forceGc) {
                    logcat { "Forcing a garbage collection to run while VPN is restarting" }
                    System.gc()
                }
            } else if (forceRestart) {
                logcat { "VPN log: starting service" }
                startVpnService(applicationContext)
            }
        }

        private const val ACTION_START_VPN = "ACTION_START_VPN"
        private const val ACTION_STOP_VPN = "ACTION_STOP_VPN"
        private const val ACTION_RESTART_VPN = "ACTION_RESTART_VPN"
        private const val ACTION_ALWAYS_ON_START = "android.net.VpnService"
    }
}
