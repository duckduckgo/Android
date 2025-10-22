/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.pixels

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

interface AutoconsentPixelManager {
    fun fireDailyPixel(pixelName: AutoConsentPixel)
    suspend fun isDetectedByPatternsProcessed(instanceId: String): Boolean
    suspend fun markDetectedByPatternsProcessed(instanceId: String)
    suspend fun isDetectedByBothProcessed(instanceId: String): Boolean
    suspend fun markDetectedByBothProcessed(instanceId: String)
    suspend fun isDetectedOnlyRulesProcessed(instanceId: String): Boolean
    suspend fun markDetectedOnlyRulesProcessed(instanceId: String)
}

@SingleInstanceIn(AppScope::class)
class RealAutoconsentPixelManager @Inject constructor(
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val autoconsentFeature: AutoconsentFeature,
    private val dispatcherProvider: DispatcherProvider,
) : AutoconsentPixelManager {

    private val detectedByPatternsCache = mutableSetOf<String>()
    private val detectedByBothCache = mutableSetOf<String>()
    private val detectedOnlyRulesCache = mutableSetOf<String>()
    private val pixelCounter = mutableMapOf<String, Int>()
    private var summaryJob: Job? = null
    private val mutex = Mutex()

    override fun fireDailyPixel(pixelName: AutoConsentPixel) {
        appCoroutineScope.launch {
            val isEnabled = withContext(dispatcherProvider.io()) {
                autoconsentFeature.cpmPixels().isEnabled()
            }
            if (!isEnabled) return@launch

            mutex.withLock {
                pixelCounter[pixelName.pixelName] = (pixelCounter[pixelName.pixelName] ?: 0) + 1

                if (summaryJob == null) {
                    summaryJob = appCoroutineScope.launch {
                        delay(2.minutes)
                        mutex.withLock {
                            pixel.enqueueFire(AutoConsentPixel.AUTOCONSENT_SUMMARY, parameters = buildSummaryParameters())
                            clearAllCaches()
                        }
                    }
                }
            }

            pixel.fire(pixelName, type = Daily())
        }
    }

    override suspend fun isDetectedByPatternsProcessed(instanceId: String): Boolean {
        return mutex.withLock {
            detectedByPatternsCache.contains(instanceId)
        }
    }

    override suspend fun markDetectedByPatternsProcessed(instanceId: String) {
        mutex.withLock {
            detectedByPatternsCache.add(instanceId)
        }
    }

    override suspend fun isDetectedByBothProcessed(instanceId: String): Boolean {
        return mutex.withLock {
            detectedByBothCache.contains(instanceId)
        }
    }

    override suspend fun markDetectedByBothProcessed(instanceId: String) {
        mutex.withLock {
            detectedByBothCache.add(instanceId)
        }
    }

    override suspend fun isDetectedOnlyRulesProcessed(instanceId: String): Boolean {
        return mutex.withLock {
            detectedOnlyRulesCache.contains(instanceId)
        }
    }

    override suspend fun markDetectedOnlyRulesProcessed(instanceId: String) {
        mutex.withLock {
            detectedOnlyRulesCache.add(instanceId)
        }
    }

    private fun clearAllCaches() {
        pixelCounter.clear()
        detectedByPatternsCache.clear()
        detectedByBothCache.clear()
        detectedOnlyRulesCache.clear()
        summaryJob?.cancel()
        summaryJob = null
    }

    private fun buildSummaryParameters(): Map<String, String> {
        val summaryParams = mutableMapOf<String, String>()
        pixelCounter.forEach { (pixelName, count) ->
            val name = pixelName
                .removePrefix("m_autoconsent_")
                .removeSuffix("_daily")
            summaryParams[name] = count.toString()
        }
        return summaryParams
    }
}
