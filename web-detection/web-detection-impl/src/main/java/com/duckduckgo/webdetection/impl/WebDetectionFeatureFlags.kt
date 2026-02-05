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
 * Feature flags for web detection telemetry.
 */
interface WebDetectionFeatureFlags {
    /**
     * @return true if adwall telemetry pixels should be fired
     */
    fun adwallTelemetryPixelEnabled(): Boolean

    /**
     * @return true if zero-count pixels should be fired (for 28-day baseline period)
     */
    fun adwallZeroCountPixelEnabled(): Boolean
}

@ContributesBinding(AppScope::class)
class RealWebDetectionFeatureFlags @Inject constructor() : WebDetectionFeatureFlags {
    // TODO: Connect to privacy config feature flags
    override fun adwallTelemetryPixelEnabled(): Boolean = true

    override fun adwallZeroCountPixelEnabled(): Boolean = true
}
