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

package com.duckduckgo.mobile.android.vpn.processor.udp

import android.util.LruCache
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.di.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnMemoryCollectorPlugin
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import timber.log.Timber
import javax.inject.Inject

@VpnScope
class UdpChannelCache @Inject constructor() : LruCache<String, UdpPacketProcessor.ChannelDetails>(500), VpnMemoryCollectorPlugin {
    override fun entryRemoved(evicted: Boolean, key: String?, oldValue: UdpPacketProcessor.ChannelDetails?, newValue: UdpPacketProcessor.ChannelDetails?) {
        Timber.i("UDP channel cache entry removed: $key. Evicted? $evicted")
        if (evicted) {
            oldValue?.datagramChannel?.close()
        }
    }

    override fun collectMemoryMetrics(): Map<String, String> {
        return mutableMapOf<String, String>().apply {
            this["udpChannelCacheSize"] = size().toString()
        }
    }
}

@Module
@ContributesTo(VpnObjectGraph::class)
abstract class UdpChannelCacheModule {
    @Binds
    @IntoSet
    abstract fun bindUdpChannelCacheMemoryCollector(udpChannelCache: UdpChannelCache): VpnMemoryCollectorPlugin
}
