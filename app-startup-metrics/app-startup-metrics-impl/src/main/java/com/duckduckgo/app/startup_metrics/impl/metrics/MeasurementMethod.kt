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

/**
 * Tracks which measurement approach was used for transparency and debugging.
 */
enum class MeasurementMethod {
    /**
     * ApplicationStartInfo system API measurement (Android 15+).
     */
    API_35_NATIVE,

    /**
     * Manual timing with heuristics (Android < 15).
     */
    LEGACY_MANUAL,
}
