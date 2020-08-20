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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.duckduckgo.mobile.android.vpn.processor.QueueMonitor
import com.duckduckgo.mobile.android.vpn.processor.TunPacketReader
import com.duckduckgo.mobile.android.vpn.processor.TunPacketWriter
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor
import com.duckduckgo.mobile.android.vpn.processor.udp.UdpPacketProcessor
import com.duckduckgo.mobile.android.vpn.ui.notification.VpnNotificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import timber.log.Timber
import xyz.hexene.localvpn.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.*


class PassthroughVpnService : VpnService(), CoroutineScope by MainScope(), NetworkChannelCreator {

    val queues = VpnQueues()

    private var tunInterface: ParcelFileDescriptor? = null
    private val binder: VpnServiceBinder = VpnServiceBinder()


    val udpPacketProcessor = UdpPacketProcessor(queues, this)
    val tcpPacketProcessor = TcpPacketProcessor(queues, this)

    private val tcpSelector = Selector.open()
    private val udpSelector = Selector.open()

    private var executorService: ExecutorService? = null
    private val tcpInput = TCPInput(queues.networkToDevice, tcpSelector)
    private val tcpOutput = TCPOutput(queues.tcpDeviceToNetwork, queues.networkToDevice, tcpSelector, this)
    private val udpOutput = UDPOutput(queues.udpDeviceToNetwork, udpSelector, this)
    private val udpInput = UDPInput(queues.networkToDevice, udpSelector)
    //private var tunPacketProcessor: TunPacketProcessor? = null

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
                udpInput,
                udpOutput,
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
            //addDnsServer("8.8.8.8")
            addAllowedApplication("com.duckduckgo.networkrequestor")
            addAllowedApplication("meteor.test.and.grade.internet.connection.speed")
            addAllowedApplication("org.zwanoo.android.speedtest")
            addAllowedApplication("com.netflix.Speedtest")
            configureMeteredConnection()
            establish()
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

        private const val ACTION_START_VPN = "ACTION_START_VPN"
        private const val ACTION_STOP_VPN = "ACTION_STOP_VPN"

        const val FOREGROUND_VPN_SERVICE_ID = 200
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

    val networkToDevice: BlockingQueue<ByteBuffer> = LinkedBlockingQueue<ByteBuffer>()
    //val networkToDevice: ConcurrentLinkedQueue<ByteBuffer> = ConcurrentLinkedQueue()

    fun clearAll() {
        tcpDeviceToNetwork.clear()
        udpDeviceToNetwork.clear()
        networkToDevice.clear()
    }
}
