/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.startup_metrics.impl.metrics

import android.os.Build
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Collects CPU architecture information for device classification.
 */
interface CpuCollector {
    /**
     * Collect CPU architecture of the device.
     *
     * @return CPU architecture (e.g., "arm64-v8a", "x86_64"), or null if collection fails
     */
    fun collectCpuArchitecture(): String?
}

@ContributesBinding(AppScope::class)
class RealCpuCollector @Inject constructor() : CpuCollector {
    override fun collectCpuArchitecture(): String? {
        return try {
            val supportedAbis = Build.SUPPORTED_ABIS
            if (supportedAbis.isNotEmpty()) {
                supportedAbis[0]
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
