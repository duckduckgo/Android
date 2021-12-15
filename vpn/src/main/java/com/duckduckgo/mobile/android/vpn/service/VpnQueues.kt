/*
 * Copyright (c) 2021 DuckDuckGo
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

import android.os.SystemClock
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import xyz.hexene.localvpn.Packet
import java.nio.ByteBuffer
import java.util.concurrent.BlockingDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import dagger.SingleInstanceIn
import kotlin.math.min

interface VpnQueuesTimeLogger {
    /**
     * This method effectively returns the time since last buffer delivered to VPN
     */
    fun millisSinceLastDeviceToNetworkWrite(): Long

    /**
     * This method effectively returns the time since last buffer written back to TUN
     */
    fun millisSinceLastNetworkToDeviceWrite(): Long

    /**
     * This method effectively returns the time since last buffer was processed
     */
    fun millisSinceLastBufferRead(): Long
}

// This is in the App object graph because we need to share it
// in more than one dagger subcomponent
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class VpnQueues @Inject constructor() : VpnQueuesTimeLogger {
    val tcpDeviceToNetwork: BlockingDeque<Packet> = LoggingLinkedBlockingDeque()
    val udpDeviceToNetwork: BlockingQueue<Packet> = LoggingLinkedBlockingDeque()

    val networkToDevice: BlockingDeque<ByteBuffer> = LoggingLinkedBlockingDeque()

    override fun millisSinceLastDeviceToNetworkWrite(): Long {
        return min(
            (tcpDeviceToNetwork as LoggingLinkedBlockingDeque).millisSinceLastOffer(),
            (udpDeviceToNetwork as LoggingLinkedBlockingDeque).millisSinceLastOffer()
        )
    }

    override fun millisSinceLastNetworkToDeviceWrite(): Long {
        return (networkToDevice as LoggingLinkedBlockingDeque).millisSinceLastOffer()
    }

    override fun millisSinceLastBufferRead(): Long {
        return minOf(
            (tcpDeviceToNetwork as LoggingLinkedBlockingDeque).millisSinceLastTake(),
            (udpDeviceToNetwork as LoggingLinkedBlockingDeque).millisSinceLastTake(),
            (networkToDevice as LoggingLinkedBlockingDeque).millisSinceLastTake(),
        )
    }

    fun clearAll() {
        tcpDeviceToNetwork.clear()
        udpDeviceToNetwork.clear()
        networkToDevice.clear()
    }
}

private class LoggingLinkedBlockingDeque<T> : LinkedBlockingDeque<T>() {
    private var lastOffer: Long? = null
    private var lastTake: Long? = null

    override fun offer(e: T): Boolean {
        updateLastOffer()
        return super.offer(e)
    }

    override fun offer(e: T, timeout: Long, unit: TimeUnit?): Boolean {
        updateLastOffer()
        return super.offer(e, timeout, unit)
    }

    override fun take(): T {
        updateLastTake()
        return super.take()
    }

    override fun takeFirst(): T {
        updateLastTake()
        return super.takeFirst()
    }

    override fun takeLast(): T {
        updateLastTake()
        return super.takeLast()
    }

    fun millisSinceLastOffer(): Long {
        return (lastOffer?.let { SystemClock.elapsedRealtime() - it } ?: Long.MAX_VALUE)
    }

    fun millisSinceLastTake(): Long {
        return (lastTake?.let { SystemClock.elapsedRealtime() - it } ?: Long.MAX_VALUE)
    }

    private fun updateLastOffer() {
        lastOffer = SystemClock.elapsedRealtime()
    }

    private fun updateLastTake() {
        lastTake = SystemClock.elapsedRealtime()
    }
}
