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

package com.duckduckgo.duckchat.impl.pixel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.di.DuckChat
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority.WARN
import logcat.logcat
import java.io.IOException
import javax.inject.Inject

/**
 * Counts the distinct local days the user swipes up for a new Duck.ai chat, and reports the
 * [MILESTONE_DAYS] once per install. Counting stops there: afterwards the count only tells that the user swiped before.
 */
@SingleIn(AppScope::class)
class SwipeUpNewChatUsage @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pixel: Pixel,
) {
    /** Records a swipe and returns whether the user had swiped before, or null if the usage couldn't be read. */
    suspend fun recordSwipe(): Boolean? {
        val today = currentTimeProvider.localDateTimeNow().toLocalDate().toEpochDay()
        var dayCountBefore = 0
        var dayCount = 0
        try {
            store.edit { preferences ->
                dayCountBefore = preferences[DAY_COUNT] ?: 0
                dayCount = dayCountBefore
                if (dayCountBefore < MILESTONE_DAYS && preferences[LAST_DAY] != today) {
                    dayCount = dayCountBefore + 1
                    preferences[LAST_DAY] = today
                    preferences[DAY_COUNT] = dayCount
                }
            }
        } catch (e: IOException) {
            logcat(WARN) { "Duck.ai swipe up new chat: usage write failed: ${e.message}" }
            return null
        }
        if (dayCount == MILESTONE_DAYS && dayCountBefore < MILESTONE_DAYS) {
            pixel.enqueueFire(DuckChatPixelName.DUCK_CHAT_SWIPE_NEW_CHAT_USED_3_DAYS, type = Pixel.PixelType.Unique())
        }
        return dayCountBefore > 0
    }

    companion object {
        const val MILESTONE_DAYS = 3

        private val LAST_DAY = longPreferencesKey("DUCK_AI_SWIPE_UP_NEW_CHAT_LAST_DAY")
        private val DAY_COUNT = intPreferencesKey("DUCK_AI_SWIPE_UP_NEW_CHAT_DAY_COUNT")
    }
}
