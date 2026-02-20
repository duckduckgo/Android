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

package com.duckduckgo.app.startup_metrics.impl.lifecycle

import android.app.Activity
import android.os.Bundle
import com.duckduckgo.app.startup_metrics.impl.StartupType
import com.duckduckgo.app.startup_metrics.impl.android.ProcessStartupTimeProvider
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import logcat.logcat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Lifecycle observer that detects startup type and resets the baseline for WARM/HOT starts.
 *
 * Note: Pre-API 35, distinguishing WARM from HOT is unreliable, so we treat both as WARM.
 */
@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class StartupTypeDetectionLifecycleObserver @Inject constructor(
    private val processStartupTimeProvider: ProcessStartupTimeProvider,
) : ActivityLifecycleCallbacks {

    private val activeActivityCount = AtomicInteger(0)
    private val startedActivityCount = AtomicInteger(0)
    private val shouldResetBaseline = AtomicBoolean(false)

    @Volatile
    private var currentStartupType: StartupType = StartupType.COLD

    /**
     * Returns the detected startup type for the current launch session.
     */
    fun getDetectedStartupType(): StartupType = currentStartupType

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Check if this is the first activity in a new foreground session
        val isFirstActivityInSession = activeActivityCount.incrementAndGet() == 1

        if (!isFirstActivityInSession) {
            // Not the first activity, nothing to do
            return
        }

        if (shouldResetBaseline.get()) {
            currentStartupType = StartupType.WARM
            processStartupTimeProvider.resetToCurrentTime()
            logcat { "WARM start detected - app returned from background, reset current time" }
        } else {
            currentStartupType = StartupType.COLD
            logcat { "COLD start detected - first activity ever in this process" }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        val startedCount = startedActivityCount.incrementAndGet()

        // Consume the reset flag after first activity starts
        if (startedCount == 1 && shouldResetBaseline.get()) {
            // This handles the case where activity wasn't destroyed
            if (currentStartupType == StartupType.COLD) {
                // onCreate wasn't called, so activity was not destroyed
                currentStartupType = StartupType.WARM
                processStartupTimeProvider.resetToCurrentTime()
                logcat { "WARM/HOT start detected - activity resumed/recreated, reset baseline" }
            }
            shouldResetBaseline.set(false)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        val startedCount = startedActivityCount.decrementAndGet()
        if (startedCount == 0) {
            shouldResetBaseline.set(true)
            logcat { "All activities stopped - app in background, next launch will be WARM" }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        val remainingActivities = activeActivityCount.decrementAndGet()
        if (remainingActivities == 0 && !activity.isChangingConfigurations) {
            startedActivityCount.set(0) // Reset started count
            logcat { "All activities destroyed - next launch will be WARM start" }
        }
    }
}
