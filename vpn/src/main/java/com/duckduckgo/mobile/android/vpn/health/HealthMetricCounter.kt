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

package com.duckduckgo.mobile.android.vpn.health

import android.content.Context
import androidx.room.Room
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.di.VpnCoroutineScope
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.ADD_TO_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.ADD_TO_TCP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.ADD_TO_UDP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.REMOVE_FROM_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.REMOVE_FROM_TCP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.REMOVE_FROM_UDP_DEVICE_TO_NETWORK_QUEUE
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.SOCKET_CHANNEL_CONNECT_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.SOCKET_CHANNEL_READ_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.SOCKET_CHANNEL_WRITE_EXCEPTION
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_READ
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.TUN_WRITE_IO_EXCEPTION
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * HealthMetricCounter is used to temporarily store raw health metrics
 *
 * APIs in here allow key health events to be
 *   - recorded as they happen. e.g., a socket exception.
 *   - queried later for a given time window
 */
@SingleInstanceIn(AppScope::class)
class HealthMetricCounter @Inject constructor(
    val context: Context,
    @VpnCoroutineScope val coroutineScope: CoroutineScope,
) {

    private val db = Room.inMemoryDatabaseBuilder(context, HealthStatsDatabase::class.java).build()
    private val healthStatsDao = db.healthStatDao()
    private val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    fun clearAllMetrics() {
        coroutineScope.launch(databaseDispatcher) {
            db.clearAllTables()
        }
    }

    fun onTunPacketReceived() {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.insertEvent(TUN_READ())
        }
    }

    fun onWrittenToDeviceToNetworkQueue(isUdp: Boolean = false) {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.insertEvent(ADD_TO_DEVICE_TO_NETWORK_QUEUE())
            healthStatsDao.insertEvent(
                if (isUdp) ADD_TO_UDP_DEVICE_TO_NETWORK_QUEUE() else ADD_TO_TCP_DEVICE_TO_NETWORK_QUEUE()
            )
        }
    }

    fun onReadFromDeviceToNetworkQueue(isUdp: Boolean = false) {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.insertEvent(REMOVE_FROM_DEVICE_TO_NETWORK_QUEUE())
            healthStatsDao.insertEvent(
                if (isUdp) REMOVE_FROM_UDP_DEVICE_TO_NETWORK_QUEUE() else REMOVE_FROM_TCP_DEVICE_TO_NETWORK_QUEUE()
            )
        }
    }

    fun onSocketChannelReadError() {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.insertEvent(SOCKET_CHANNEL_READ_EXCEPTION())
        }
    }

    fun onSocketChannelWriteError() {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.insertEvent(SOCKET_CHANNEL_WRITE_EXCEPTION())
        }
    }

    fun onSocketChannelConnectError() {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.insertEvent(SOCKET_CHANNEL_CONNECT_EXCEPTION())
        }
    }

    fun onTunWriteIOException() {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.insertEvent(TUN_WRITE_IO_EXCEPTION())
        }
    }

    fun getStat(
        type: SimpleEvent,
        recentTimeThresholdMillis: Long
    ): Long {
        return healthStatsDao.eventCount(type.type, recentTimeThresholdMillis)
    }

    fun purgeOldMetrics() {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.purgeOldMetrics()
        }
    }
}
