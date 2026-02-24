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

package com.duckduckgo.app.startup_metrics.impl.collectors

import com.duckduckgo.app.startup_metrics.impl.StartupMetricEvent
import com.duckduckgo.app.startup_metrics.impl.StartupType

/**
 * Unified interface for collecting startup metrics across all Android API levels.
 *
 * This interface abstracts away the implementation differences between:
 * - **API 35+**: Native ApplicationStartInfo API (accurate, system-level)
 * - **API < 35**: Lifecycle-based manual measurement (fallback)
 *
 * The correct implementation is selected at runtime based on device API level
 * to avoid class loading issues with API 35+ specific types on older devices.
 */
interface StartupCollector {
    /**
     * Collect complete startup metrics.
     *
     * @return Complete startup metric event
     */
    suspend fun collectStartupMetrics(): StartupMetricEvent?
}
