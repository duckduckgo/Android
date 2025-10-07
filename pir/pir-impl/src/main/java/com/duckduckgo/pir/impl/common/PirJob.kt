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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.impl.callbacks.PirCallbacks
import logcat.logcat

abstract class PirJob(
    private val callbacks: PluginPoint<PirCallbacks>,
) {
    fun onJobStarted() {
        callbacks.getPlugins().forEach {
            logcat { "PIR-CALLBACKS: Starting $it" }
            it.onPirJobStarted()
        }
    }

    fun onJobCompleted() {
        callbacks.getPlugins().forEach {
            logcat { "PIR-CALLBACKS: Completing $it" }
            it.onPirJobCompleted()
        }
    }

    fun onJobStopped() {
        callbacks.getPlugins().forEach {
            logcat { "PIR-CALLBACKS: Stopping $it" }
            it.onPirJobStopped()
        }
    }

    enum class RunType {
        MANUAL,
        SCHEDULED,
        OPTOUT,
        EMAIL_CONFIRMATION,
    }
}
