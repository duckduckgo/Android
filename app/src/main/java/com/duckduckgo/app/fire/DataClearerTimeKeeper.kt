/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.fire

import android.os.SystemClock
import com.duckduckgo.app.settings.clear.ClearWhenOption
import timber.log.Timber

interface BackgroundTimeKeeper {
    fun hasEnoughTimeElapsed(
        timeNow: Long = SystemClock.elapsedRealtime(),
        backgroundedTimestamp: Long,
        clearWhenOption: ClearWhenOption
    ): Boolean
}

class DataClearerTimeKeeper : BackgroundTimeKeeper {

    override fun hasEnoughTimeElapsed(
        timeNow: Long,
        backgroundedTimestamp: Long,
        clearWhenOption: ClearWhenOption
    ): Boolean {
        if (clearWhenOption == ClearWhenOption.APP_EXIT_ONLY) return false

        val elapsedTime = timeSinceAppBackgrounded(timeNow, backgroundedTimestamp)
        Timber.i(
            "It has been ${elapsedTime}ms since the app was backgrounded. Current configuration is for $clearWhenOption")

        return elapsedTime >= clearWhenOption.durationMilliseconds()
    }

    private fun timeSinceAppBackgrounded(timeNow: Long, backgroundedTimestamp: Long): Long {
        return timeNow - backgroundedTimestamp
    }
}
