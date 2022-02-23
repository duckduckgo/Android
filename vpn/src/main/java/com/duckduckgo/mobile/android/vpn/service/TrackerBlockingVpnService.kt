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
import android.os.Binder
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.system.OsConstants.AF_INET6
import androidx.core.content.ContextCompat
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.processor.TunPacketReader
import com.duckduckgo.mobile.android.vpn.processor.TunPacketWriter
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor
import com.duckduckgo.mobile.android.vpn.processor.udp.UdpPacketProcessor
import com.duckduckgo.mobile.android.vpn.service.state.VpnStateMonitorService
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldEnabledNotificationBuilder
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationFactory
import com.duckduckgo.mobile.android.vpn.ui.notification.OngoingNotificationPressedHandler
import dagger.android.AndroidInjection
import dummy.ui.VpnPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.StandardSocketOptions
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

class TrackerBlockingVpnService : VpnService(), CoroutineScope by MainScope(), NetworkChannelCreator {

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

    @Inject
    lateinit var queues: VpnQueues

    private var tunInterface: ParcelFileDescriptor? = null

    private val binder: VpnServiceBinder = VpnServiceBinder()

    @Inject
    lateinit var udpPacketProcessorFactory: UdpPacketProcessor.Factory
    lateinit var udpPacketProcessor: UdpPacketProcessor

    @Inject
    lateinit var tcpPacketProcessorFactory: TcpPacketProcessor.Factory
    private lateinit var tcpPacketProcessor: TcpPacketProcessor
    private var executorService: ExecutorService? = null

    @Inject
    lateinit var tunPacketReaderFactory: TunPacketReader.Factory

    @Inject
    lateinit var tunPacketWriterFactory: TunPacketWriter.Factory
    private var vpnStateServiceReference: IBinder? = null

    @Inject lateinit var appBuildConfig: AppBuildConfig

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

        udpPacketProcessor = udpPacketProcessorFactory.build(this)
        tcpPacketProcessor = tcpPacketProcessorFactory.build(this, this)

        Timber.e("VPN log onCreate")
    }

    override fun onBind(intent: Intent?): IBinder {
        Timber.i("VPN log onBind invoked")
        return binder
    }

    override fun onUnbind(p0: Intent?): Boolean {
        Timber.i("VPN log onUnbind invoked")
        return super.onUnbind(p0)
    }

    override fun onDestroy() {
        Timber.e("VPN log onDestroy")
        super.onDestroy()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        Timber.e("VPN log onStartCommand: ${intent?.action}")

        var returnCode: Int = Service.START_NOT_STICKY

        when (val action = intent?.action) {
            ACTION_START_VPN, ACTION_ALWAYS_ON_START -> {
                notifyVpnStart()
                launch { startVpn() }
                returnCode = Service.START_REDELIVER_INTENT
            }
            ACTION_STOP_VPN -> {
                launch { stopVpn(VpnStateMonitor.VpnStopReason.SELF_STOP) }
            }
            else -> Timber.e("Unknown intent action: $action")
        }

        return returnCode
    }

    private suspend fun startVpn() = withContext(Dispatchers.IO) {
        Timber.i("VPN log: Starting VPN")

        queues.clearAll()

        establishVpnInterface()

        if (tunInterface == null) {
            Timber.e("Failed to establish the TUN interface")
            deviceShieldPixels.vpnEstablishTunInterfaceError()
            return@withContext
        }

        tunInterface?.let { vpnInterface ->
            executorService?.shutdownNow()
            val processors = listOf(
                tcpPacketProcessor,
                udpPacketProcessor,
                tunPacketReaderFactory.create(vpnInterface),
                tunPacketWriterFactory.create(vpnInterface)
            )
            executorService = Executors.newFixedThreadPool(processors.size).also { executorService ->
                processors.forEach { executorService.submit(it) }
            }
        }

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

            VpnRoutes.includedRoutes.forEach { addRoute(it.address, it.maskWidth) }
            // Add the route for all Global Unicast Addresses. This is the IPv6 equivalent to
            // IPv4 public IP addresses. They are addresses that routable in the internet
            addRoute("2000::", 3)

            setBlocking(true)
            // Cap the max MTU value to avoid backpressure issues in the socket
            // This is effectively capping the max segment size too
            setMtu(16_384)
            configureMeteredConnection()

            if (vpnPreferences.isCustomDnsServerSet()) {
                addDnsServer("1.1.1.1").also { Timber.i("Using custom DNS server (1.1.1.1)") }
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
            stopVpn(VpnStateMonitor.VpnStopReason.ERROR)
        }
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

    private suspend fun stopVpn(reason: VpnStateMonitor.VpnStopReason) = withContext(Dispatchers.IO) {
        Timber.i("VPN log: Stopping VPN. $reason")

        queues.clearAll()
        executorService?.shutdownNow()
        udpPacketProcessor.stop()
        tcpPacketProcessor.stop()
        tunInterface?.close()
        tunInterface = null

        sendStopPixels(reason)

        vpnServiceCallbacksPluginPoint.getPlugins().forEach {
            Timber.v("VPN log: stopping ${it.javaClass} callback")
            it.onVpnStopped(this, reason)
        }

        vpnStateServiceReference?.let {
            unbindService(vpnStateServiceConnection).also { vpnStateServiceReference = null }
        }

        stopForeground(true)
        stopSelf()
    }

    private fun sendStopPixels(reason: VpnStateMonitor.VpnStopReason) {
        when (reason) {
            VpnStateMonitor.VpnStopReason.SELF_STOP -> { /* noop */
            }
            VpnStateMonitor.VpnStopReason.ERROR -> deviceShieldPixels.startError()
            VpnStateMonitor.VpnStopReason.REVOKED -> deviceShieldPixels.suddenKillByVpnRevoked()
        }
    }

    private fun VpnService.Builder.configureMeteredConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setMetered(false)
        }
    }

    override fun onRevoke() {
        Timber.e("VPN log onRevoke called")
        launch { stopVpn(VpnStateMonitor.VpnStopReason.REVOKED) }
    }

    override fun onLowMemory() {
        Timber.e("VPN log onLowMemory called")
    }

    // https://developer.android.com/reference/android/app/Service.html#onTrimMemory(int)
    override fun onTrimMemory(level: Int) {
        Timber.e("VPN log onTrimMemory level $level called")

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

        const val ACTION_VPN_REMINDER = "com.duckduckgo.vpn.internaltesters.reminder"
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
        fun isServiceRunning(context: Context): Boolean {
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

        fun startService(context: Context) {
            val applicationContext = context.applicationContext

            if (isServiceRunning(applicationContext)) return

            startIntent(applicationContext).run {
                ContextCompat.startForegroundService(applicationContext, this)
            }
        }

        fun stopService(context: Context) {
            val applicationContext = context.applicationContext

            if (!isServiceRunning(applicationContext)) return

            stopIntent(applicationContext).run {
                ContextCompat.startForegroundService(applicationContext, this)
            }
        }

        suspend fun restartVpnService(
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

    override fun createDatagramChannel(): DatagramChannel {
        return DatagramChannel.open().also { channel ->
            channel.configureBlocking(false)
            channel.socket().let { socket ->
                protect(socket)
                socket.broadcast = true
            }
        }
    }

    @SuppressLint("NewApi") // because we use the appBuildConfig.sdkInt IDE doesn't detect it
    override fun createSocketChannel(): SocketChannel {
        return SocketChannel.open().also { channel ->
            if (appBuildConfig.sdkInt >= VERSION_CODES.N) {
                channel.setOption(StandardSocketOptions.SO_REUSEADDR, true)
            }
            channel.configureBlocking(false)
            protect(channel.socket())
        }
    }

    private val INCLUDED_APPS_FOR_TESTING = listOf(
        "com.duckduckgo.networkrequestor",
        "com.cdrussell.networkrequestor",
        "meteor.test.and.grade.internet.connection.speed",
        "org.zwanoo.android.speedtest",
        "com.netflix.Speedtest",
        "eu.vspeed.android",
        "net.fireprobe.android",
        "com.philips.lighting.hue2"
    )
}

interface NetworkChannelCreator {
    fun createDatagramChannel(): DatagramChannel
    fun createSocketChannel(): SocketChannel
}
