/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.store

import org.junit.Assert.assertEquals
import org.junit.Test
import org.threeten.bp.LocalDateTime

class DatabaseDateFormatterTest {

    @Test
    fun whenBucketingByHourOnlyHourlyPrecisionIsUsed() {
        val formatted = DatabaseDateFormatter.bucketByHour(fixedTime())
        assertEquals("2020-12-25T13:00:00", formatted)
    }

    @Test
    fun whenBucketingByTimestampOnlySecondsPrecisionIsUsed() {
        val formatted = DatabaseDateFormatter.timestamp(fixedTime())
        assertEquals("2020-12-25T13:14:15", formatted)
    }

    private fun fixedTime(): LocalDateTime {
        return LocalDateTime.of(2020, 12, 25, 13, 14, 15, 16)
    }
}
