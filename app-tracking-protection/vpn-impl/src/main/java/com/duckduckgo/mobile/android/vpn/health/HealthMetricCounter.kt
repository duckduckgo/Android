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
import com.duckduckgo.mobile.android.vpn.health.SimpleEvent.Companion.NO_VPN_CONNECTIVITY
import com.duckduckgo.vpn.di.VpnCoroutineScope
import dagger.SingleInstanceIn
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

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

    fun onVpnConnectivityError() {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.insertEvent(NO_VPN_CONNECTIVITY())
        }
    }

    fun getStat(
        type: SimpleEvent,
        recentTimeThresholdMillis: Long,
    ): Long {
        return healthStatsDao.eventCount(type.type, recentTimeThresholdMillis)
    }

    fun purgeOldMetrics() {
        coroutineScope.launch(databaseDispatcher) {
            healthStatsDao.purgeOldMetrics()
        }
    }
}
