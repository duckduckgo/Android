/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.internal.network

import android.content.Context
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
import com.duckduckgo.networkprotection.impl.config.PcapConfig
import com.duckduckgo.networkprotection.impl.config.RealNetPDefaultConfigProvider
import com.duckduckgo.networkprotection.internal.feature.NetPInternalFeatureToggles
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import javax.inject.Inject

@ContributesBinding(
    scope = VpnScope::class,
    rank = ContributesBinding.RANK_HIGHEST,
)
class NetPInternalDefaultConfigProvider @Inject constructor(
    private val realNetPConfigProvider: RealNetPDefaultConfigProvider,
    private val mtuInternalProvider: NetPInternalMtuProvider,
    private val exclusionListProvider: NetPInternalExclusionListProvider,
    private val netPInternalFeatureToggles: NetPInternalFeatureToggles,
    private val context: Context,
) : NetPDefaultConfigProvider {

    override fun mtu(): Int {
        return mtuInternalProvider.getMtu()
    }

    override suspend fun exclusionList(): Set<String> {
        return mutableSetOf<String>().apply {
            addAll(realNetPConfigProvider.exclusionList())
            addAll(exclusionListProvider.getExclusionList())
        }.toSet()
    }

    override fun fallbackDns(): Set<InetAddress> {
        return realNetPConfigProvider.fallbackDns()
    }

    override fun pcapConfig(): PcapConfig? {
        return if (netPInternalFeatureToggles.enablePcapRecording().isEnabled()) {
            PcapConfig(
                filename = context.netpGetPcapFile().absolutePath,
                snapLen = mtu() + 16,
                fileSize = 2 * 1024 * 1024,
            )
        } else {
            null
        }
    }

    override suspend fun routes(): Map<String, Int> {
        return realNetPConfigProvider.routes()
    }
}

private const val WIREGIARD_PCAP = "wiregiard.pcap"

fun Context.netpGetPcapFile(): File {
    return File(getDir("data", Context.MODE_PRIVATE), WIREGIARD_PCAP)
}

/**
 * Deletes the PCAP file if exists
 * @return `true` if file existed and was delete, `false` otherwise
 */
fun Context.netpDeletePcapFile(): Boolean {
    File(getDir("data", Context.MODE_PRIVATE), WIREGIARD_PCAP).run {
        if (netpPcapFileHasContent()) {
            FileOutputStream(absoluteFile).close()
            return true
        }
    }
    return false
}

fun Context.netpPcapFileHasContent(): Boolean {
    File(getDir("data", Context.MODE_PRIVATE), WIREGIARD_PCAP).run {
        return exists() && length() > 0
    }
}
