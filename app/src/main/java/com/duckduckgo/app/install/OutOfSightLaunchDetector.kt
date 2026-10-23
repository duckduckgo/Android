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

import android.view.Display
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

interface OutOfSightLaunchDetector {
    /**
     * True once this process's launch activity was found on a display the user can't see, which happens when a store
     * launches the app while installing it.
     */
    val isOutOfSightLaunch: Boolean

    /**
     * @return true, and remembers it for this process, if the launch activity is on [display] and the user can't see it
     */
    fun onLaunchDisplay(display: Display?): Boolean
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealOutOfSightLaunchDetector @Inject constructor() : OutOfSightLaunchDetector {

    @Volatile
    override var isOutOfSightLaunch: Boolean = false
        private set

    override fun onLaunchDisplay(display: Display?): Boolean {
        if (display == null) return false

        // Samsung's Galaxy Store starts the app on its own private virtual display while installing it, then wipes the app's
        // data. Only a private display's owner can show anything on it. Public secondary displays, such as Samsung DeX or an
        // external screen, are ones the user sees.
        val outOfSight = display.displayId != Display.DEFAULT_DISPLAY && (display.flags and Display.FLAG_PRIVATE) != 0
        if (outOfSight) {
            isOutOfSightLaunch = true
            logcat(INFO) { "Launched out of sight on display ${display.displayId} (${display.name})" }
        }
        return outOfSight
    }
}
