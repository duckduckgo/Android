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

package com.duckduckgo.app.startup_metrics.impl.android

import android.os.SystemClock
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Provides the startup time baseline used for calculating application launch duration.
 */
interface ProcessStartupTimeProvider {
    /**
     * Returns the current startup baseline time in milliseconds (uptimeMillis basis).
     *
     * @return Baseline time in milliseconds for duration calculation
     */
    fun getStartUptimeMillis(): Long

    /**
     * Resets the baseline to the current uptime.
     */
    fun resetToCurrentTime()
}

/**
 * Implementation that provides a dynamic baseline for startup measurements.
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealProcessStartupTimeProvider @Inject constructor() : ProcessStartupTimeProvider {
    companion object {
        /**
         * Captured when this class first loads - very early in process lifecycle.
         * This provides the most accurate COLD start baseline.
         */
        @Volatile
        @JvmStatic
        private var classLoadedUptimeMs: Long = SystemClock.uptimeMillis()
    }

    /**
     * Current baseline for startup measurement.
     * Initially set to class load time, but can be reset for WARM/HOT starts.
     */
    @Volatile
    private var startTimeMs: Long = classLoadedUptimeMs

    override fun getStartUptimeMillis(): Long = startTimeMs

    override fun resetToCurrentTime() {
        startTimeMs = SystemClock.uptimeMillis()
    }
}
