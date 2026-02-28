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

import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Provides the time at which the process was started, in milliseconds since boot.
 */
interface ProcessTimeProvider {
    /**
     * Returns the time at which the process was started, in milliseconds since boot.
     */
    fun startupTimeMs(): Long

    /**
     * Returns the current uptime in milliseconds since boot.
     * This is used to calculate the time since startup.
     */
    fun currentUptimeMs(): Long
}

@ContributesBinding(AppScope::class)
class RealProcessTimeProvider @Inject constructor() : ProcessTimeProvider {
    override fun startupTimeMs(): Long = if (Build.VERSION.SDK_INT >= 33) {
        Process.getStartRequestedUptimeMillis()
    } else {
        Process.getStartUptimeMillis()
    }

    override fun currentUptimeMs(): Long {
        return SystemClock.uptimeMillis()
    }
}
