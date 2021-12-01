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

import android.os.Debug
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.service.VpnMemoryCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import timber.log.Timber
import javax.inject.Inject

@SingleInstanceIn(VpnObjectGraph::class)
@ContributesMultibinding(VpnObjectGraph::class)
class ProcessMemoryCollector @Inject constructor() : VpnMemoryCollectorPlugin {
    override fun collectMemoryMetrics(): Map<String, String> {
        Timber.v("Collecting process memory data")

        val metrics = mutableMapOf<String, String>()

        with(Runtime.getRuntime()) {
            val heapMax = (maxMemory() / KB)
            val heapAllocated = (totalMemory() - freeMemory()) / KB
            val heapRemaining = heapMax - heapAllocated
            metrics["javaHeapMaxSizeKb"] = heapMax.toString()
            metrics["javaHeapAllocatedKb"] = heapAllocated.toString()
            metrics["javaHeapRemainingKb"] = heapRemaining.toString()
        }

        metrics["nativeHeapSizeKb"] = (Debug.getNativeHeapSize() / KB).toString()
        metrics["nativeHeapAllocatedKb"] = (Debug.getNativeHeapAllocatedSize() / KB).toString()
        metrics["nativeHeapRemainingKb"] = (Debug.getNativeHeapFreeSize() / KB).toString()

        return metrics
    }

    companion object {
        private const val KB = 1024
    }
}
