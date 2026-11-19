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

package com.duckduckgo.app.trackerdetection.blocklist

import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle

/**
 * The blocklist experiments ship alternative tracker blocklists to a cohort of users. Consumers
 * need to report which experiment is active and to fire its conversion metrics. Code outside
 * tracker detection should ask here rather than read the toggles, so it measures the same
 * experiment the blocklist uses.
 */
interface BlockListExperiment {
    /**
     * The active blocklist experiment as `<featureName>_<cohortName>`, or null when the user is
     * not enrolled in one.
     */
    suspend fun activeExperimentCohort(): String?

    /**
     * The conversion metric [metric] for the active blocklist experiment, or null when the user is
     * not enrolled in one. Fire it with [MetricsPixel.send].
     */
    suspend fun metric(metric: Metric): MetricsPixel?

    /**
     * The toggle of the active blocklist experiment, or null when none is active, for firing
     * conversion metrics the experiment declares in its own settings. The user may not be enrolled
     * in it; [MetricsPixel.send] already sends nothing then.
     */
    suspend fun activeExperiment(): Toggle?

    enum class Metric {
        TWO_X_REFRESH,
        THREE_X_REFRESH,
    }
}
