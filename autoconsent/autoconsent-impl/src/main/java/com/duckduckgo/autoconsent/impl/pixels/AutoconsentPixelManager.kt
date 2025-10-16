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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class AutoconsentPixelManager @Inject constructor(
    private val pixel: Pixel,
) {

    private val detectedByPatternsCache = mutableSetOf<String>()
    private val detectedByBothCache = mutableSetOf<String>()
    private val detectedOnlyRulesCache = mutableSetOf<String>()

    fun fireDailyPixel(pixelName: AutoConsentPixel) {
        pixel.fire(pixelName, type = Pixel.PixelType.Daily())
    }

    fun isDetectedByPatternsProcessed(instanceId: String): Boolean {
        return detectedByPatternsCache.contains(instanceId)
    }

    fun markDetectedByPatternsProcessed(instanceId: String) {
        detectedByPatternsCache.add(instanceId)
    }

    fun isDetectedByBothProcessed(instanceId: String): Boolean {
        return detectedByBothCache.contains(instanceId)
    }

    fun markDetectedByBothProcessed(instanceId: String) {
        detectedByBothCache.add(instanceId)
    }

    fun isDetectedOnlyRulesProcessed(instanceId: String): Boolean {
        return detectedOnlyRulesCache.contains(instanceId)
    }

    fun markDetectedOnlyRulesProcessed(instanceId: String) {
        detectedOnlyRulesCache.add(instanceId)
    }
}
