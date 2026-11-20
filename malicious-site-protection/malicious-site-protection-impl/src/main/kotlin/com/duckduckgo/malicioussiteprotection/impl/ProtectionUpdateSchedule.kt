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

package com.duckduckgo.malicioussiteprotection.impl

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.random.Random

@SingleIn(AppScope::class)
class ProtectionUpdateSchedule @Inject constructor(context: Context) {
    private val preferences = context.getSharedPreferences("protection_update_schedule", Context.MODE_PRIVATE)

    @Synchronized
    fun clear(tag: String) {
        preferences.edit().remove(tag).commit()
    }

    @Synchronized
    fun delayMillis(tag: String, intervalMinutes: Long): Long {
        val fraction = if (preferences.contains(tag)) {
            preferences.getInt(tag, 0)
        } else {
            Random.nextInt(50_001).also { preferences.edit().putInt(tag, it).commit() }
        }
        val intervalMillis = TimeUnit.MINUTES.toMillis(intervalMinutes).coerceAtLeast(0)
        return intervalMillis / 1_000_000 * fraction + intervalMillis % 1_000_000 * fraction / 1_000_000
    }
}
