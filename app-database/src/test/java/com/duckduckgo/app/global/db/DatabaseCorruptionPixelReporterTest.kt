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
import com.duckduckgo.app.global.db.DatabasePixelName.DATABASE_CORRUPTION_COUNT
import com.duckduckgo.app.global.db.DatabasePixelName.DATABASE_CORRUPTION_DAILY
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class DatabaseCorruptionPixelReporterTest {

    private val pixel: Pixel = mock()
    private val db: SupportSQLiteDatabase = mock()

    @Test
    fun whenOpenDatabaseReportedThenFiresCountAndDailyPixels() {
        whenever(db.isOpen).thenReturn(true)

        DatabaseCorruptionPixelReporter(lazyOf(pixel), processName = "main").report("app", db)

        val params = mapOf("database" to "app", "process_name" to "main", "database_open" to "true")
        verify(pixel).fire(DATABASE_CORRUPTION_COUNT, params, emptyMap(), Count)
        verify(pixel).fire(DATABASE_CORRUPTION_DAILY, params, emptyMap(), Daily())
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenUnopenedDatabaseReportedInSecondaryProcessThenParamsReflectIt() {
        whenever(db.isOpen).thenReturn(false)

        DatabaseCorruptionPixelReporter(lazyOf(pixel), processName = ":vpn").report("app", db)

        val params = mapOf("database" to "app", "process_name" to ":vpn", "database_open" to "false")
        verify(pixel).fire(DATABASE_CORRUPTION_COUNT, params, emptyMap(), Count)
        verify(pixel).fire(DATABASE_CORRUPTION_DAILY, params, emptyMap(), Daily())
    }
}
