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

package com.duckduckgo.app.browser.newaddressbaroption

import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.TimeUnit

class RealDuckAiToggleMigrationPixelsTest {

    private val pixel: Pixel = mock()
    private val testee = RealDuckAiToggleMigrationPixels(pixel)

    @Test
    fun `when flip reported then fires count and daily pixels`() {
        testee.reportFlipped()

        verify(pixel).fire(AppPixelName.DUCK_AI_TOGGLE_MIGRATION_FLIPPED_COUNT)
        verify(pixel).fire(AppPixelName.DUCK_AI_TOGGLE_MIGRATION_FLIPPED_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun `when reverted within a day then buckets as le_1d`() = assertRevertBucket(TimeUnit.HOURS.toMillis(3), "le_1d")

    @Test
    fun `when reverted exactly one day after then buckets as le_1d`() = assertRevertBucket(TimeUnit.DAYS.toMillis(1), "le_1d")

    @Test
    fun `when reverted within a week then buckets as le_7d`() = assertRevertBucket(TimeUnit.DAYS.toMillis(7), "le_7d")

    @Test
    fun `when reverted within a month then buckets as le_30d`() = assertRevertBucket(TimeUnit.DAYS.toMillis(30), "le_30d")

    @Test
    fun `when reverted after a month then buckets as gt_30d`() = assertRevertBucket(TimeUnit.DAYS.toMillis(31), "gt_30d")

    @Test
    fun `when reverted by turning the toggle off then reports how as toggle_off`() = assertRevertHow(MigrationRevert.TOGGLE_OFF, "toggle_off")

    @Test
    fun `when reverted by turning Duck AI off then reports how as duckai_off`() = assertRevertHow(MigrationRevert.DUCKAI_OFF, "duckai_off")

    private fun assertRevertBucket(millisSinceFlip: Long, expected: String) {
        testee.reportReverted(millisSinceFlip, MigrationRevert.TOGGLE_OFF)

        verify(pixel).fire(
            AppPixelName.DUCK_AI_TOGGLE_MIGRATION_REVERTED,
            parameters = mapOf("days_since_flip" to expected, "how" to "toggle_off"),
        )
    }

    private fun assertRevertHow(how: MigrationRevert, expected: String) {
        testee.reportReverted(TimeUnit.HOURS.toMillis(3), how)

        verify(pixel).fire(
            AppPixelName.DUCK_AI_TOGGLE_MIGRATION_REVERTED,
            parameters = mapOf("days_since_flip" to "le_1d", "how" to expected),
        )
    }
}
