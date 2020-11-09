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
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.duckduckgo.mobile.android.vpn.BuildConfig
import com.duckduckgo.mobile.android.vpn.model.VpnStateUpdate
import com.duckduckgo.mobile.android.vpn.model.VpnStats
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.processor.TunPacketReader
import com.duckduckgo.mobile.android.vpn.processor.TunPacketWriter
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.LocalIpAddressDetector
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetector
import com.duckduckgo.mobile.android.vpn.processor.udp.UdpPacketProcessor
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.ui.notification.VpnNotificationBuilder
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
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
import javax.inject.Inject

class PassthroughVpnService : VpnService(), CoroutineScope by MainScope(), NetworkChannelCreator {

    @Inject
    lateinit var packetPersister: PacketPersister

    @Inject
    lateinit var trackerDetector: VpnTrackerDetector

    @Inject
    lateinit var vpnDatabase: VpnDatabase

    @Inject
    lateinit var repository: AppTrackerBlockingStatsRepository

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    @Inject
    lateinit var localAddressDetector: LocalIpAddressDetector

    private val queues = VpnQueues()

    private var tunInterface: ParcelFileDescriptor? = null
    private val binder: VpnServiceBinder = VpnServiceBinder()

    lateinit var udpPacketProcessor: UdpPacketProcessor
    lateinit var tcpPacketProcessor: TcpPacketProcessor

    private var executorService: ExecutorService? = null

    private lateinit var trackersBlocked: LiveData<List<VpnTrackerAndCompany>>
    private val trackersBlockedObserver = Observer<List<VpnTrackerAndCompany>> { onNewTrackerBlocked(it) }

    inner class VpnServiceBinder : Binder() {

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == LAST_CALL_TRANSACTION) {
                onRevoke()
                return true
            }
            return false
        }

        fun getService(): PassthroughVpnService {
            return this@PassthroughVpnService
        }
    }

    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)

        udpPacketProcessor = UdpPacketProcessor(queues, this)
        tcpPacketProcessor = TcpPacketProcessor(queues, this, trackerDetector, packetPersister, localAddressDetector)

        Timber.i("VPN onCreate")
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.i("VPN onBind invoked")
        return binder
    }

    override fun onUnbind(p0: Intent?): Boolean {
        Timber.i("VPN onUnbind invoked")
        return super.onUnbind(p0)
    }

    override fun onDestroy() {
        Timber.i("VPN onDestroy")
        if (trackersBlocked.hasActiveObservers()) {
            trackersBlocked.removeObserver(trackersBlockedObserver)
        }
        notifyVpnStopped()
        super.onDestroy()
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
        notifyVpnStart()

        tickerJob?.cancel()
        queues.clearAll()

        establishVpnInterface()
        schedulerReminderAlarm()
        hideReminderNotification()

        tunInterface?.let { vpnInterface ->
            executorService?.shutdownNow()
            val processors = listOf(
                tcpPacketProcessor,
                udpPacketProcessor,
                TunPacketReader(vpnInterface, queues),
                TunPacketWriter(vpnInterface, queues)
            )
            executorService = Executors.newFixedThreadPool(processors.size).also { executorService ->
                processors.forEach { executorService.submit(it) }
            }
        }
    }

    private fun onNewTrackerBlocked(trackersBlocked: List<VpnTrackerAndCompany>) {
        val notification = VpnNotificationBuilder.buildPersistentNotification(this, trackersBlocked)
        notificationManager.notify(FOREGROUND_VPN_SERVICE_ID, notification)
    }

    private fun hideReminderNotification() {
        notificationManager.cancel(VPN_REMINDER_NOTIFICATION_ID)
    }

    private fun schedulerReminderAlarm() {
        val receiver = ComponentName(this, VpnReminderReceiver::class.java)

        packageManager.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        val alarmIntent = Intent(this, VpnReminderReceiver::class.java).let { intent ->
            intent.action = ACTION_VPN_REMINDER
            PendingIntent.getBroadcast(this, 0, intent, 0)
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        alarmManager?.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis(),
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            alarmIntent
        )
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
            val limitingToTestApps = false
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
        notifyVpnStopped()
        safelyRemoveTrackersObserver()

        tickerJob?.cancel()
        queues.clearAll()
        executorService?.shutdownNow()
        udpPacketProcessor.stop()
        tcpPacketProcessor.stop()
        tunInterface?.close()
        tunInterface = null

        stopForeground(true)
        stopSelf()
    }

    private fun safelyRemoveTrackersObserver(){
        if (::trackersBlocked.isInitialized){
            if (trackersBlocked.hasActiveObservers()) {
                trackersBlocked.removeObserver(trackersBlockedObserver)
            }
        }
    }

    private fun VpnService.Builder.configureMeteredConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setMetered(false)
        }
    }

    override fun onRevoke() {
        Timber.w("VPN onRevoke called")
        stopVpn()
        super.onRevoke()
    }

    private fun notifyVpnStart() {
        launch {
            withContext(Dispatchers.IO) {
                val currentStats = vpnDatabase.vpnStatsDao().getCurrent()
                if (currentStats == null) {
                    vpnDatabase.vpnStatsDao().insert(
                        VpnStats(
                            id = 0,
                            startedAt = OffsetDateTime.now(),
                            lastUpdated = OffsetDateTime.now(),
                            timeRunning = 0,
                            dataSent = 0,
                            dataReceived = 0,
                            packetsSent = 0,
                            packetsReceived = 0
                        )
                    )
                    Timber.w("VPN: First ever start at ${OffsetDateTime.now()}")
                } else {
                    vpnDatabase.vpnStatsDao().updateLastUpdated(OffsetDateTime.now(), currentStats.id)
                    Timber.w("VPN: started at ${OffsetDateTime.now()}")
                }
                vpnDatabase.vpnStateDao().update(VpnStateUpdate(isRunning = true))
            }

            trackersBlocked = withContext(Dispatchers.IO) { repository.getTodaysTrackersBlocked() }
            trackersBlocked.observeForever(trackersBlockedObserver)

            val trackersBlocked = withContext(Dispatchers.IO) { repository.getTodaysTrackersBlockedSync() }
            startForeground(FOREGROUND_VPN_SERVICE_ID, VpnNotificationBuilder.buildPersistentNotification(applicationContext, trackersBlocked))
        }
    }

    private fun notifyVpnStopped() {
        launch(Dispatchers.IO) {
            vpnDatabase.vpnStateDao().update(VpnStateUpdate(isRunning = false))
            val currentStats = vpnDatabase.vpnStatsDao().getCurrent()
            currentStats?.let {
                val lastStart = currentStats.lastUpdated
                val timeRunning = lastStart.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
                vpnDatabase.vpnStatsDao().updateTimeRunning(timeRunning, OffsetDateTime.now(), currentStats.id)
                Timber.w("VPN: stopped after $timeRunning millis running")
            }
        }
    }

    companion object {

        const val ACTION_VPN_REMINDER = "com.duckduckgo.vpn.internaltesters.reminder"
        const val VPN_REMINDER_NOTIFICATION_ID = 999

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
        fun isServiceRunning(context: Context): Boolean {
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

        private val EXCLUDED_PROBLEMATIC_APPS = listOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.instagram.android"
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

        private val EXCLUDED_APPS =
            listOf(BuildConfig.LIBRARY_PACKAGE_NAME).plus(EXCLUDED_SYSTEM_APPS).plus(EXCLUDED_PROBLEMATIC_APPS).plus(MAJOR_BROWSERS)

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