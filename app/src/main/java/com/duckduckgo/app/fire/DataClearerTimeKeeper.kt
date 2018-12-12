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

import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import timber.log.Timber
import javax.inject.Inject

interface BackgroundTimeKeeper {
    fun hasEnoughTimeElapsed(timeNow: Long = System.currentTimeMillis(), selectedOptionDurationMillis: Long): Boolean
}

class DataClearerTimeKeeper @Inject constructor(private val settingsDataStore: SettingsDataStore) : BackgroundTimeKeeper {

    override fun hasEnoughTimeElapsed(timeNow: Long, selectedOptionDurationMillis: Long): Boolean {
        if (!settingsDataStore.hasBackgroundTimestampRecorded()) return false

        val clearWhenOption = settingsDataStore.automaticallyClearWhenOption

        val elapsedTime = timeSinceAppBackgrounded(settingsDataStore, timeNow)
        Timber.i("It has been ${elapsedTime}ms since the app was backgrounded. Current configuration is for $clearWhenOption")

        return when (clearWhenOption) {
            ClearWhenOption.APP_EXIT_ONLY -> true
            else -> elapsedTime >= selectedOptionDurationMillis
        }
    }

    private fun timeSinceAppBackgrounded(settingsDataStore: SettingsDataStore, timeNow: Long): Long {
        val timestamp = settingsDataStore.appBackgroundedTimestamp
        return timeNow - timestamp
    }
}