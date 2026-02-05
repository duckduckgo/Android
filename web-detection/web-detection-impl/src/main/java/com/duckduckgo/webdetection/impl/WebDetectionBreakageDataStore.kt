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

package com.duckduckgo.webdetection.impl

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Stores detection results for inclusion in breakage reports.
 *
 * Detections are cleared when consumed (e.g., when a breakage report is submitted).
 */
interface WebDetectionBreakageDataStore {
    /**
     * Add a detection to the store.
     * @param detectorId The full detector ID (e.g., "adwalls.generic")
     */
    fun addDetection(detectorId: String)

    /**
     * Get all detected items as a comma-separated string for the breakage report.
     * @return Comma-separated list of detector IDs, or null if empty
     */
    fun getDetectionsForBreakageReport(): String?

    /**
     * Clear all stored detections.
     */
    fun clearDetections()
}

@ContributesBinding(AppScope::class)
class RealWebDetectionBreakageDataStore @Inject constructor() : WebDetectionBreakageDataStore {

    private val detections = mutableSetOf<String>()

    override fun addDetection(detectorId: String) {
        synchronized(detections) {
            detections.add(detectorId)
        }
    }

    override fun getDetectionsForBreakageReport(): String? {
        synchronized(detections) {
            return if (detections.isEmpty()) null else detections.joinToString(",")
        }
    }

    override fun clearDetections() {
        synchronized(detections) {
            detections.clear()
        }
    }
}
