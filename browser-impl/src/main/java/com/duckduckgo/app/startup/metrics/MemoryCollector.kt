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

package com.duckduckgo.app.startup.metrics

import android.app.ActivityManager
import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Collects device specification metrics for app startup analysis.
 */
interface MemoryCollector {
    /**
     * Collect device memory capacity bucket for classification.
     *
     * @return RAM bucket (e.g., "4GB", "8GB"), or null if collection fails
     */
    fun collectDeviceRamBucket(): String?
}

@ContributesBinding(AppScope::class)
class RealMemoryCollector @Inject constructor(
    private val context: Context,
) : MemoryCollector {

    companion object {
        private const val BYTES_PER_GiB = 1024 * 1024 * 1024L
    }

    override fun collectDeviceRamBucket(): String? {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return null
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalRamGiB = (memoryInfo.totalMem.toDouble() / BYTES_PER_GiB).roundToLong()
            bucketRamSize(totalRamGiB)
        } catch (_: Exception) {
            null
        }
    }

    private fun bucketRamSize(ramGiB: Long): String {
        return when {
            ramGiB < 1L -> "<1GB"
            ramGiB < 2L -> "1GB"
            ramGiB < 4L -> "2GB"
            ramGiB < 6L -> "4GB"
            ramGiB < 8L -> "6GB"
            ramGiB < 12L -> "8GB"
            ramGiB < 16L -> "12GB"
            else -> "16GB+"
        }
    }
}
