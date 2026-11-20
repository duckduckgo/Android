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

package com.duckduckgo.agesignals.impl

import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Failed
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.CHECK_COUNT
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.CHECK_DAILY
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.PROMPT_SHOWN_COUNT
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.PROMPT_SHOWN_DAILY
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.PETAL
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.PETAL_RANDOMIZE
import javax.inject.Inject

enum class AgeSignalsPixelName(override val pixelName: String) : Pixel.PixelName {
    CHECK_COUNT("m_age_signals_check_count"),
    CHECK_DAILY("m_age_signals_check_daily"),
    PROMPT_SHOWN_COUNT("m_age_signals_prompt_shown_count"),
    PROMPT_SHOWN_DAILY("m_age_signals_prompt_shown_daily"),
}

class AgeSignalsPixels @Inject constructor(
    private val pixel: Pixel,
) {

    fun fireCheck(outcome: AgeSignalsOutcome) {
        val result = when (outcome) {
            is Failed -> mapOf(PARAM_RESULT to FAILURE, PARAM_ERROR_CODE to outcome.error.pixelValue)
            else -> mapOf(PARAM_RESULT to SUCCESS)
        }
        val dailyTag = (listOf(CHECK_DAILY.pixelName) + result.values).joinToString(":")
        fireCountAndDaily(CHECK_COUNT, CHECK_DAILY, result + RANDOMIZED_TIMESTAMP, dailyTag)
    }

    fun firePromptShown() {
        fireCountAndDaily(PROMPT_SHOWN_COUNT, PROMPT_SHOWN_DAILY, RANDOMIZED_TIMESTAMP)
    }

    private fun fireCountAndDaily(
        count: AgeSignalsPixelName,
        daily: AgeSignalsPixelName,
        parameters: Map<String, String>,
        dailyTag: String? = null,
    ) {
        pixel.fire(count, parameters)
        pixel.fire(daily, parameters, type = Pixel.PixelType.Daily(dailyTag))
    }

    companion object {
        const val PARAM_RESULT = "result"
        const val PARAM_ERROR_CODE = "error_code"
        const val SUCCESS = "success"
        const val FAILURE = "failure"
        private val RANDOMIZED_TIMESTAMP = mapOf(PETAL to PETAL_RANDOMIZE)
    }
}
