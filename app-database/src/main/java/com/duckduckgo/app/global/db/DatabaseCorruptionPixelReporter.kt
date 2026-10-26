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

package com.duckduckgo.app.global.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.app.global.db.DatabasePixelName.DATABASE_CORRUPTION_COUNT
import com.duckduckgo.app.global.db.DatabasePixelName.DATABASE_CORRUPTION_DAILY
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import javax.inject.Inject

class DatabaseCorruptionPixelReporter @Inject constructor(
    // Lazy because the pixel sender persists pending pixels in app.db, so an eager Pixel would make
    // AppDatabase depend on itself.
    private val pixel: Lazy<Pixel>,
    @param:ProcessName private val processName: String,
) {

    fun report(databaseName: String, db: SupportSQLiteDatabase) {
        // Fired, not enqueued: enqueueing would write to the database that was just found corrupt.
        val params = mapOf(
            PARAM_DATABASE to databaseName,
            PARAM_PROCESS_NAME to processName,
            PARAM_DATABASE_OPEN to db.isOpen.toString(),
        )
        pixel.value.fire(DATABASE_CORRUPTION_COUNT, params)
        pixel.value.fire(DATABASE_CORRUPTION_DAILY, params, type = Daily())
    }

    private companion object {
        const val PARAM_DATABASE = "database"
        const val PARAM_PROCESS_NAME = "process_name"
        const val PARAM_DATABASE_OPEN = "database_open"
    }
}
