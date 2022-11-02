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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.network.util.asRoute
import com.duckduckgo.mobile.android.vpn.network.util.getActiveNetwork
import com.duckduckgo.mobile.android.vpn.network.util.getSystemActiveNetworkDefaultDns
import com.duckduckgo.mobile.android.vpn.network.util.isLocal
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.prefs.VpnPreferences
import com.duckduckgo.mobile.android.vpn.service.state.VpnStateMonitorService
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldEnabledNotificationBuilder
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationFactory
import com.duckduckgo.mobile.android.vpn.ui.notification.OngoingNotificationPressedHandler
import dagger.android.AndroidInjection
import kotlinx.coroutines.*
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

@Suppress("NoHardcodedCoroutineDispatcher")
@InjectWith(VpnScope::class)
class TrackerBlockingVpnService : VpnService(), CoroutineScope by MainScope() {

    @Inject
    lateinit var vpnPreferences: VpnPreferences

    @Inject
    lateinit var deviceShieldExcludedApps: TrackingProtectionAppsRepository

    @Inject
    lateinit var deviceShieldNotificationFactory: DeviceShieldNotificationFactory

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var ongoingNotificationPressedHandler: OngoingNotificationPressedHandler

    @Inject
    lateinit var vpnServiceCallbacksPluginPoint: PluginPoint<VpnServiceCallbacks>

    @Inject
    lateinit var memoryCollectorPluginPoint: PluginPoint<VpnMemoryCollectorPlugin>

    private var tunInterface: ParcelFileDescriptor? = null

    private val binder: VpnServiceBinder = VpnServiceBinder()

    private var vpnStateServiceReference: IBinder? = null

    @Inject lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var appTpFeatureConfig: AppTpFeatureConfig

    @Inject lateinit var vpnNetworkStack: VpnNetworkStack

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

    private val vpnStateServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            Timber.d("Connected to state monitor service")
            vpnStateServiceReference = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("Disconnected from state monitor service")
            vpnStateServiceReference = null
        }
    }

    inner class VpnServiceBinder : Binder() {

        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int
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

        Timber.d("VPN log onCreate, creating the ${vpnNetworkStack.name} network stack")
        vpnNetworkStack.onCreateVpn().getOrThrow()
    }

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("VPN log onBind invoked")
        return binder
    }

    override fun onUnbind(p0: Intent?): Boolean {
        Timber.d("VPN log onUnbind invoked")
        return super.onUnbind(p0)
    }

    override fun onDestroy() {
        Timber.d("VPN log onDestroy")
        vpnNetworkStack.onDestroyVpn()
        super.onDestroy()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        Timber.d("VPN log onStartCommand: ${intent?.action}")

        var returnCode: Int = Service.START_NOT_STICKY

        when (val action = intent?.action) {
            ACTION_START_VPN, ACTION_ALWAYS_ON_START -> {
                notifyVpnStart()
                launch { startVpn() }
                returnCode = Service.START_REDELIVER_INTENT
            }
            ACTION_STOP_VPN -> {
                launch { stopVpn(VpnStopReason.SELF_STOP) }
            }
            else -> Timber.e("Unknown intent action: $action")
        }

        return returnCode
    }

    private suspend fun startVpn() = withContext(Dispatchers.IO) {
        Timber.d("VPN log: Starting VPN")

        establishVpnInterface()

        if (tunInterface == null) {
            Timber.e("Failed to establish the TUN interface")
            deviceShieldPixels.vpnEstablishTunInterfaceError()
            return@withContext
        }

        if (isInterceptDnsTrafficEnabled) {
            applicationContext.getActiveNetwork()?.let { an ->
                Timber.v("Setting underlying network $an")
                setUnderlyingNetworks(arrayOf(an))
            }
        } else {
            Timber.v("NetworkSwitchHandling disabled...skip setting underlying network")
        }

        vpnNetworkStack.onStartVpn(tunInterface!!).getOrThrow()

        vpnServiceCallbacksPluginPoint.getPlugins().forEach {
            Timber.v("VPN log: starting ${it.javaClass} callback")
            it.onVpnStarted(this)
        }

        Intent(applicationContext, VpnStateMonitorService::class.java).also {
            bindService(it, vpnStateServiceConnection, Context.BIND_AUTO_CREATE)
        }

    }

    private suspend fun establishVpnInterface() {
        tunInterface = Builder().run {
            addAddress("10.0.0.2", 32)

            // Add IPv6 Unique Local Address
            addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)

            // Allow IPv6 to go through the VPN
            // See https://developer.android.com/reference/android/net/VpnService.Builder#allowFamily(int) for more info as to why
            allowFamily(AF_INET6)

            val dnsList = getDns()

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
                            Timber.d("Adding DNS address $it to VPN routes")
                            vpnRoutes.add(it)
                        }
                    }
                }
                vpnRoutes.forEach { addRoute(it.address, it.maskWidth) }
            }

            // Add the route for all Global Unicast Addresses. This is the IPv6 equivalent to
            // IPv4 public IP addresses. They are addresses that routable in the internet
            addRoute("2000::", 3)

            setBlocking(true)
            // Cap the max MTU value to avoid backpressure issues in the socket
            // This is effectively capping the max segment size too
            setMtu(vpnNetworkStack.mtu())
            configureMeteredConnection()

            // Set DNS
            dnsList.forEach { addr ->
                if (isIpv6SupportEnabled || addr is Inet4Address) {
                    Timber.v("Adding DNS $addr")
                    runCatching {
                        addDnsServer(addr)
                    }.onFailure { t ->
                        Timber.e(t, "Error setting DNS $addr")
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

            // Can either route all apps through VPN and exclude a few (better for prod), or exclude all apps and include a few (better for dev)
            val limitingToTestApps = false
            if (limitingToTestApps) {
                safelyAddAllowedApps(INCLUDED_APPS_FOR_TESTING)
                Timber.w("Limiting VPN to test apps only:\n${INCLUDED_APPS_FOR_TESTING.joinToString(separator = "\n") { it }}")
            } else {
                safelyAddDisallowedApps(
                    deviceShieldExcludedApps.getExclusionAppsList()
                )
            }

            // Apparently we always need to call prepare, even tho not clear in docs
            // without this prepare, establish() returns null after device reboot
            prepare(this@TrackerBlockingVpnService.applicationContext)
            establish()
        }

        if (tunInterface == null) {
            Timber.e("VPN log: Failed to establish VPN tunnel")
            stopVpn(VpnStopReason.ERROR)
        }
    }

    private fun getDns(): Set<InetAddress> {
        // private extension function, this is purposely here to limit visibility
        fun Set<InetAddress>.containsIpv4(): Boolean {
            forEach {
                if (it is Inet4Address) return true
            }
            return false
        }

        val dns = mutableSetOf<InetAddress>()

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
                    Timber.d("Adding cloudflare DNS")
                    dns.add(InetAddress.getByName("1.1.1.1"))
                    dns.add(InetAddress.getByName("1.0.0.1"))
                    if (isIpv6SupportEnabled) {
                        dns.add(InetAddress.getByName("2606:4700:4700::1111"))
                        dns.add(InetAddress.getByName("2606:4700:4700::1001"))
                    }
                }.onFailure {
                    Timber.w(it, "Error adding fallback DNS")
                }
            }

            // always add ipv4 DNS
            if (!dns.containsIpv4()) {
                Timber.d("DNS set does not contain IPv4, adding cloudflare")
                kotlin.runCatching {
                    dns.add(InetAddress.getByName("1.1.1.1"))
                    dns.add(InetAddress.getByName("1.0.0.1"))
                }.onFailure {
                    Timber.w(it, "Error adding fallback DNS")
                }
            }
        }

        if (!dns.containsIpv4()) {
            // never allow IPv6-only DNS
            Timber.v("No IPv4 DNS found, return empty DNS list")
            return setOf()
        }

        return dns.toSet()
    }

    private fun Builder.safelyAddAllowedApps(apps: List<String>) {
        for (app in apps) {
            try {
                addAllowedApplication(app)
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.w("Package name not found: %s", app)
            }
        }
    }

    private fun Builder.safelyAddDisallowedApps(apps: List<String>) {
        for (app in apps) {
            try {
                Timber.v("Excluding app from VPN: $app")
                addDisallowedApplication(app)
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.w("Package name not found: %s", app)
            }
        }
    }

    private suspend fun stopVpn(reason: VpnStopReason) = withContext(Dispatchers.IO) {
        Timber.d("VPN log: Stopping VPN. $reason")

        vpnNetworkStack.onStopVpn()

        tunInterface?.close()
        tunInterface = null

        sendStopPixels(reason)

        vpnServiceCallbacksPluginPoint.getPlugins().forEach {
            Timber.v("VPN log: stopping ${it.javaClass} callback")
            it.onVpnStopped(this, reason)
        }

        vpnStateServiceReference?.let {
            runCatching { unbindService(vpnStateServiceConnection).also { vpnStateServiceReference = null } }
        }

        stopForeground(true)
        stopSelf()
    }

    private fun sendStopPixels(reason: VpnStopReason) {
        when (reason) {
            VpnStopReason.SELF_STOP, VpnStopReason.UNKNOWN -> { /* noop */
            }
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
        Timber.w("VPN log onRevoke called")
        launch { stopVpn(VpnStopReason.REVOKED) }
    }

    override fun onLowMemory() {
        Timber.w("VPN log onLowMemory called")
    }

    // https://developer.android.com/reference/android/app/Service.html#onTrimMemory(int)
    override fun onTrimMemory(level: Int) {
        Timber.d("VPN log onTrimMemory level $level called")

        // Collect memory data info from memory collectors
        val memoryData = mutableMapOf<String, String>()
        memoryCollectorPluginPoint.getPlugins().forEach { memoryData.putAll(it.collectMemoryMetrics()) }

        if (memoryData.isEmpty()) {
            Timber.v("VPN log nothing to send from memory collectors")
            return
        }

        when (level) {
            TRIM_MEMORY_BACKGROUND -> deviceShieldPixels.vpnProcessExpendableLow(memoryData)
            TRIM_MEMORY_MODERATE -> deviceShieldPixels.vpnProcessExpendableModerate(memoryData)
            TRIM_MEMORY_COMPLETE -> deviceShieldPixels.vpnProcessExpendableComplete(memoryData)
            TRIM_MEMORY_RUNNING_MODERATE -> deviceShieldPixels.vpnMemoryRunningModerate(memoryData)
            TRIM_MEMORY_RUNNING_LOW -> deviceShieldPixels.vpnMemoryRunningLow(memoryData)
            TRIM_MEMORY_RUNNING_CRITICAL -> deviceShieldPixels.vpnMemoryRunningCritical(memoryData)
            else -> null /* noop */
        }
    }

    private fun notifyVpnStart() {
        val deviceShieldNotification = deviceShieldNotificationFactory.createNotificationDeviceShieldEnabled()
        startForeground(
            VPN_FOREGROUND_SERVICE_ID,
            DeviceShieldEnabledNotificationBuilder
                .buildDeviceShieldEnabledNotification(applicationContext, deviceShieldNotification, ongoingNotificationPressedHandler)
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

        internal suspend fun restartVpnService(
            context: Context,
            forceGc: Boolean = false
        ) = withContext(Dispatchers.Default) {
            val applicationContext = context.applicationContext
            if (isServiceRunning(applicationContext)) {
                Timber.v("VPN log: stopping service")
                stopService(applicationContext)
                // wait for the service to stop and then restart it
                waitUntilStopped(applicationContext)

                if (forceGc) {
                    Timber.d("Forcing a garbage collection to run while VPN is restarting")
                    System.gc()
                }

                Timber.v("VPN log: re-starting service")
                startService(applicationContext)
            }
        }

        private suspend fun waitUntilStopped(applicationContext: Context) {
            // it's possible `isServiceRunning` keeps returning true and we never stop waiting, the timeout ensures we don't block forever
            withTimeoutOrNull(10_000) {
                while (isServiceRunning(applicationContext)) {
                    delay(500)
                    Timber.v("VPN log: waiting for service to stop...")
                }
            }
        }

        private const val ACTION_START_VPN = "ACTION_START_VPN"
        private const val ACTION_STOP_VPN = "ACTION_STOP_VPN"
        private const val ACTION_ALWAYS_ON_START = "android.net.VpnService"
    }

    private val INCLUDED_APPS_FOR_TESTING = listOf(
        "com.duckduckgo.networkrequestor",
        "com.cdrussell.networkrequestor",
        "meteor.test.and.grade.internet.connection.speed",
        "org.zwanoo.android.speedtest",
        "com.netflix.Speedtest",
        "eu.vspeed.android",
        "net.fireprobe.android",
        "com.philips.lighting.hue2",
        "com.duckduckgo.mobile.android.debug"
    )
}
