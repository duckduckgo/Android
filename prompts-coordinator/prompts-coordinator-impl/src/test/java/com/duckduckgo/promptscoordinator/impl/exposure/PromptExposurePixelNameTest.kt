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

package com.duckduckgo.promptscoordinator.impl.exposure

import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PromptExposurePixelNameTest {

    private val pixel: Pixel = mock()

    @Test
    fun whenFiredThenEveryPixelCarriesTheVersion() {
        PromptExposurePixelName.entries.forEach { name ->
            pixel.firePromptExposurePixel(name, "key" to "value")

            val expected = buildMap {
                put("key", "value")
                put("version", "1")
                if (name.randomizeTimestamp) put(Pixel.PixelParameter.PETAL, Pixel.PixelValues.PETAL_RANDOMIZE)
            }
            verify(pixel).fire(name, expected)
        }
    }

    @Test
    fun whenFiredThenEveryPixelButTheWeeklyDenominatorIsRandomized() {
        val randomized = PromptExposurePixelName.entries.filter { it.randomizeTimestamp }

        assertEquals(
            listOf(
                PromptExposurePixelName.PROMPT_EXPOSURE,
                PromptExposurePixelName.PROMPT_SHOWN,
                PromptExposurePixelName.PROMPT_GAP,
                PromptExposurePixelName.PROMPT_SESSION,
            ),
            randomized,
        )
    }
}
