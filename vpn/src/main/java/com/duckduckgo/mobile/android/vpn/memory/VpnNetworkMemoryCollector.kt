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

package com.duckduckgo.mobile.android.vpn.memory

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnMemoryCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import timber.log.Timber
import xyz.hexene.localvpn.TCB
import java.io.RandomAccessFile
import javax.inject.Inject

@SingleInstanceIn(VpnScope::class)
@ContributesMultibinding(VpnScope::class)
class VpnNetworkMemoryCollector @Inject constructor() : VpnMemoryCollectorPlugin {
    override fun collectMemoryMetrics(): Map<String, String> {
        Timber.v("Collecting vpn network memory resources")

        val memoryStat = try {
            val reader = RandomAccessFile(PROC_STAT_FILE_PATH, "r")
            reader.readLine()
        } catch (t: Throwable) {
            Timber.e(t, "Error reading $PROC_STAT_FILE_PATH")
            null
        }

        return mutableMapOf<String, String>().apply {
            this["TCBCacheSize"] = TCB.tcbCache.size.toString()

            memoryStat?.let { memory ->
                val procMemory = memory.split(" ")
                this["vmRSSKb"] = (procMemory[1].toInt() * PAGE_KB).toString()
            }
        }
    }

    companion object {
        private const val PROC_STAT_FILE_PATH = "/proc/self/statm"
        private const val PAGE_KB = 4
    }
}
