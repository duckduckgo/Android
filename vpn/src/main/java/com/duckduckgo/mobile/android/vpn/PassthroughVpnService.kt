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

package com.duckduckgo.mobile.android.vpn

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import thirdpartyneedsrewritten.hexene.localvpn.HexenePacket
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue


class PassthroughVpnService : VpnService(), CoroutineScope by MainScope(), DatagramChannelCreator {

    private var tunInterface: ParcelFileDescriptor? = null
    private val binder: VpnServiceBinder = VpnServiceBinder()
    private val queues = VpnQueues()
    private var tunPacketProcessor: TunPacketProcessor? = null

    val deviceToNetworkPacketProcessor = DeviceToNetworkPacketProcessor(this, queues)

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

        establishVpnInterface()

        tunInterface?.let { vpnInterface ->
            running = true
            startForeground(FOREGROUND_VPN_SERVICE_ID, VpnNotificationBuilder.build(this))
            startStatTicker()

            tunPacketProcessor = TunPacketProcessor(vpnInterface, queues).also {
                GlobalScope.launch {
                    it.run()
                }
            }
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
            addAddress("192.168.0.2", 32)
            addRoute("0.0.0.0", 0)
            addDnsServer("8.8.8.8")
            configureMeteredConnection()
            establish()
        }
    }


    private fun stopVpn() {
        Timber.i("Stopping VPN")
        tickerJob?.cancel()
        tunPacketProcessor?.stop()
        deviceToNetworkPacketProcessor.stop()
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

    override fun createDatagram(): DatagramChannel {
        return DatagramChannel.open().also { datagramChannel ->
            protect(datagramChannel.socket())
            datagramChannel.configureBlocking(false)
        }
    }

}

interface DatagramChannelCreator {
    fun createDatagram(): DatagramChannel
}

class VpnQueues {
    val deviceToNetwork: BlockingQueue<HexenePacket> = LinkedBlockingQueue()
    val networkToDevice: BlockingQueue<ByteBuffer> = LinkedBlockingQueue<ByteBuffer>()
}