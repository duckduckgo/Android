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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.duckduckgo.mobile.android.vpn.BuildConfig
import com.duckduckgo.mobile.android.vpn.processor.TunPacketReader
import com.duckduckgo.mobile.android.vpn.processor.TunPacketWriter
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.AndroidHostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.PayloadBytesExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.PlaintextHostHeaderExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.ServerNameIndicationHeaderHostExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.DomainBasedTrackerDetector
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.TrackerListProvider
import com.duckduckgo.mobile.android.vpn.processor.udp.UdpPacketProcessor
import com.duckduckgo.mobile.android.vpn.ui.notification.VpnNotificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.BlockingDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue

class PassthroughVpnService : VpnService(), CoroutineScope by MainScope(), NetworkChannelCreator {

    val queues = VpnQueues()

    private var tunInterface: ParcelFileDescriptor? = null
    private val binder: VpnServiceBinder = VpnServiceBinder()

    // TODO: DI all of these
    private val hostNameHeaderExtractor = PlaintextHostHeaderExtractor()
    private val encryptedRequestHostExtractor = ServerNameIndicationHeaderHostExtractor()
    private val payloadBytesExtractor = PayloadBytesExtractor()
    private val trackerListProvider = TrackerListProvider()
    private val hostnameExtractor = AndroidHostnameExtractor(hostNameHeaderExtractor, encryptedRequestHostExtractor, payloadBytesExtractor)
    private val trackerDetector = DomainBasedTrackerDetector(hostnameExtractor, trackerListProvider.trackerList())

    val udpPacketProcessor = UdpPacketProcessor(queues, this)
    val tcpPacketProcessor = TcpPacketProcessor(queues, this, trackerDetector, hostnameExtractor)

    private var executorService: ExecutorService? = null

    inner class VpnServiceBinder : Binder() {

        fun getService(): PassthroughVpnService {
            return this@PassthroughVpnService
        }
    }

    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("onCreate")
    }

    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("onDestroy")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("onStartCommand: ${intent?.action}")

        var returnCode: Int = Service.START_NOT_STICKY

        when (val action = intent?.action) {
            ACTION_START_VPN -> {
                startVpn()
                returnCode = Service.START_REDELIVER_INTENT
            }
            ACTION_STOP_VPN -> {
                stopVpn()
            }
            else -> Timber.e("Unknown intent action: $action")
        }

        return returnCode
    }

    private fun startVpn() {
        Timber.i("Starting VPN")
        tickerJob?.cancel()

        queues.clearAll()

        establishVpnInterface()

        tunInterface?.let { vpnInterface ->
            running = true
            startForeground(FOREGROUND_VPN_SERVICE_ID, VpnNotificationBuilder.build(this))
            startStatTicker()

            executorService?.shutdownNow()
            val processors = listOf(
                //QueueMonitor(queues),
                //tcpInput,
                //tcpOutput,
                tcpPacketProcessor,
                udpPacketProcessor,
                TunPacketReader(vpnInterface, queues),
                TunPacketWriter(vpnInterface, queues)
            )
            executorService = Executors.newFixedThreadPool(processors.size).also { executorService ->
                processors.forEach { executorService.submit(it) }
            }

            //tunPacketProcessor = TunPacketProcessor(vpnInterface, queues).also { executorService.submit(it) }

            //            tunPacketProcessor = TunPacketProcessor(vpnInterface, queues).also {
            //                GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            //                    it.run()
            //                }
            //            }
        }
    }

    private fun startStatTicker() {
        //        tickerJob = launch {
        //            val startTime = System.currentTimeMillis()
        //            while (true) {

        //                Timber.v("VPN service running for ${TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime)} seconds")
        //                delay(1000)
        //            }
        //        }
    }

    private fun establishVpnInterface() {
        tunInterface = Builder().run {
            addAddress("10.0.0.2", 32)
            addRoute("0.0.0.0", 0)
            setBlocking(true)
            setMtu(Short.MAX_VALUE.toInt())
            configureMeteredConnection()
            //addDnsServer("8.8.8.8")

            // Can either route all apps through VPN and exclude a few (better for prod), or exclude all apps and include a few (better for dev)
            val limitingToTestApps = true
            if (limitingToTestApps) safelyAddAllowedApps(INCLUDED_APPS) else safelyAddDisallowedApps(EXCLUDED_APPS)

            establish()
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
                addDisallowedApplication(app)
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.w("Package name not found: %s", app)
            }
        }
    }

    private fun stopVpn() {
        Timber.i("Stopping VPN")
        tickerJob?.cancel()
        queues.clearAll()
        //tunPacketProcessor?.stop()
        executorService?.shutdownNow()
        udpPacketProcessor.stop()
        tcpPacketProcessor.stop()
        tunInterface?.close()
        tunInterface = null
        stopForeground(true)

        stopSelf()
        running = false
    }

    private fun VpnService.Builder.configureMeteredConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setMetered(false)
        }
    }

    override fun onRevoke() {
        super.onRevoke()
        Timber.w("VPN onRevoke called")
        stopVpn()
    }

    companion object {

        var running: Boolean = false

        fun serviceIntent(context: Context): Intent {
            return Intent(context, PassthroughVpnService::class.java)
        }

        fun startIntent(context: Context): Intent {
            return serviceIntent(context).also {
                it.action = ACTION_START_VPN
            }
        }

        fun stopIntent(context: Context): Intent {
            return serviceIntent(context).also {
                it.action = ACTION_STOP_VPN
            }
        }

        // This method was deprecated in API level 26. As of Build.VERSION_CODES.O,
        // this method is no longer available to third party applications.
        // For backwards compatibility, it will still return the caller's own services.
        // So for us it's still valid because we don't need to know third party services, just ours.
        @Suppress("DEPRECATION")
        fun isRunning(context: Context): Boolean {
            val manager: ActivityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if ("com.duckduckgo.mobile.android.vpn.service.PassthroughVpnService" == service.service.className) {
                    return true
                }
            }
            return false
        }

        private const val ACTION_START_VPN = "ACTION_START_VPN"
        private const val ACTION_STOP_VPN = "ACTION_STOP_VPN"

        const val FOREGROUND_VPN_SERVICE_ID = 200

        private val EXCLUDED_SYSTEM_APPS = listOf(
            "com.android.vending",
            "com.google.android.gsf.login",
            "com.google.android.googlequicksearchbox",
            "com.android.providers.downloads.ui"
        )

        private val MAJOR_BROWSERS = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.UCMobile.intl",
            "com.android.browser",
            "com.sec.android.app.sbrowser",
            "info.guardianproject.orfo",
            "org.torproject.torbrowser_alpha",
            "mobi.mgeek.TunnyBrowser",
            "com.linkbubble.playstore",
            "org.adblockplus.browser",
            "arun.com.chromer",
            "com.flynx",
            "com.ghostery.android.ghostery",
            "com.cliqz.browser",
            "om.opera.mini.native",
            "com.uc.browser.en",
            "com.chrome.beta",
            "org.mozilla.firefox_beta",
            "com.opera.browser.beta",
            "com.opera.mini.native.beta",
            "com.sec.android.app.sbrowser.beta",
            "org.mozilla.fennec_fdroid",
            "org.mozilla.rocket",
            "com.chrome.dev",
            "com.chrome.canary",
            "corg.mozilla.fennec_aurora",
            "oorg.mozilla.fennec",
            "com.google.android.apps.chrome",
            "org.chromium.chrome"
        )

        private val EXCLUDED_APPS = listOf(BuildConfig.LIBRARY_PACKAGE_NAME).plus(EXCLUDED_SYSTEM_APPS).plus(MAJOR_BROWSERS)

        private val INCLUDED_APPS = listOf(
            "com.duckduckgo.networkrequestor",
            "meteor.test.and.grade.internet.connection.speed",
            "org.zwanoo.android.speedtest",
            "com.netflix.Speedtest",
            "eu.vspeed.android",
            "net.fireprobe.android"
        )
    }

    override fun createDatagramChannel(): DatagramChannel {
        return DatagramChannel.open().also { channel ->
            protect(channel.socket())
            channel.configureBlocking(false)
        }
    }

    override fun createSocket(): SocketChannel {
        return SocketChannel.open().also { channel ->
            channel.configureBlocking(false)
            protect(channel.socket())
        }
    }
}

interface NetworkChannelCreator {
    fun createDatagramChannel(): DatagramChannel
    fun createSocket(): SocketChannel
}

class VpnQueues {
    val tcpDeviceToNetwork: BlockingDeque<Packet> = LinkedBlockingDeque()
    val udpDeviceToNetwork: BlockingQueue<Packet> = LinkedBlockingQueue()

    val networkToDevice: BlockingDeque<ByteBuffer> = LinkedBlockingDeque<ByteBuffer>()
    //val networkToDevice: ConcurrentLinkedQueue<ByteBuffer> = ConcurrentLinkedQueue()

    fun clearAll() {
        tcpDeviceToNetwork.clear()
        udpDeviceToNetwork.clear()
        networkToDevice.clear()
    }
}
