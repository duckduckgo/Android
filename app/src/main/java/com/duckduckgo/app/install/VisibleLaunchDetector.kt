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

package com.duckduckgo.app.install

import android.app.Activity
import android.os.Build
import android.view.Display
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject

interface VisibleLaunchDetector {
    /**
     * Suspends until an activity of this process has been resumed on a display the user can see. Returns at once from then on,
     * and when [ReinstallMarkerFeature] turns the check off.
     */
    suspend fun awaitVisibleLaunch()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<VisibleLaunchDetector>())
@ContributesIntoSet(AppScope::class, binding = binding<ActivityLifecycleCallbacks>())
class RealVisibleLaunchDetector @Inject constructor(
    private val reinstallMarkerFeature: ReinstallMarkerFeature,
) : VisibleLaunchDetector, ActivityLifecycleCallbacks {

    private val displaysSeen = MutableStateFlow<Set<DisplayKind>>(emptySet())

    override suspend fun awaitVisibleLaunch() {
        if (!isOn(reinstallMarkerFeature.self())) return
        val accepted = if (isOn(reinstallMarkerFeature.strictMode())) STRICT_ACCEPTED else LENIENT_ACCEPTED
        displaysSeen.first { seen -> seen.any { it in accepted } }
    }

    override fun onActivityResumed(activity: Activity) {
        val kind = kindOf(displayOf(activity))
        if (kind !in displaysSeen.value) displaysSeen.update { it + kind }
    }

    private fun isOn(toggle: Toggle): Boolean = runCatching { toggle.isEnabled() }.getOrDefault(true)

    // Some app stores, such as Samsung's, launch the app on a private display the user can't see while installing it
    private fun kindOf(display: Display?): DisplayKind = when {
        display == null -> DisplayKind.UNKNOWN
        display.displayId == Display.DEFAULT_DISPLAY -> DisplayKind.DEFAULT
        (display.flags and Display.FLAG_PRIVATE) != 0 -> DisplayKind.PRIVATE
        else -> DisplayKind.PUBLIC_SECONDARY
    }

    private fun displayOf(activity: Activity): Display? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }
    }.getOrNull()

    private enum class DisplayKind { DEFAULT, PUBLIC_SECONDARY, PRIVATE, UNKNOWN }

    private companion object {
        val STRICT_ACCEPTED = setOf(DisplayKind.DEFAULT, DisplayKind.PUBLIC_SECONDARY)
        val LENIENT_ACCEPTED = STRICT_ACCEPTED + DisplayKind.UNKNOWN
    }
}
