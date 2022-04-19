/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.bandwidth.impl

import android.net.TrafficStats
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface TrafficStatsProvider {

    fun getAppRxBytes(): Long
    fun getAppTxBytes(): Long

    fun getTotalRxBytes(): Long
    fun getTotalTxBytes(): Long
}

@ContributesBinding(AppScope::class)
class RealTrafficStatsProvider @Inject constructor() : TrafficStatsProvider {

    override fun getAppRxBytes(): Long {
        return TrafficStats.getUidRxBytes(android.os.Process.myUid())
    }

    override fun getAppTxBytes(): Long {
        return TrafficStats.getUidTxBytes(android.os.Process.myUid())
    }

    override fun getTotalRxBytes(): Long {
        return TrafficStats.getTotalRxBytes()
    }

    override fun getTotalTxBytes(): Long {
        return TrafficStats.getTotalTxBytes()
    }
}
