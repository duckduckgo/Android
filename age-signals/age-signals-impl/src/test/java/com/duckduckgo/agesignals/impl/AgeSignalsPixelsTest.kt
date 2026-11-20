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
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotApplicable
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotShared
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Shared
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.UnknownStatus
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.VerificationRequired
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.CHECK_COUNT
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.CHECK_DAILY
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.PROMPT_SHOWN_COUNT
import com.duckduckgo.agesignals.impl.AgeSignalsPixelName.PROMPT_SHOWN_DAILY
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.PETAL
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.PETAL_RANDOMIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class AgeSignalsPixelsTest {

    private val pixel: Pixel = mock()
    private val pixels = AgeSignalsPixels(pixel)

    @Test
    fun whenARunSucceedsThenTheCheckPixelSaysSuccessWithoutAnErrorCode() {
        listOf(NotApplicable, NotShared, VerificationRequired, Shared, UnknownStatus).forEach { outcome ->
            clearInvocations(pixel)

            pixels.fireCheck(outcome)

            verifyCountAndDaily(
                CHECK_COUNT,
                CHECK_DAILY,
                mapOf("result" to "success", PETAL to PETAL_RANDOMIZE),
                "m_age_signals_check_daily:success",
            )
        }
    }

    @Test
    fun whenARunFailsThenTheCheckPixelCarriesTheErrorCode() {
        val expectedCodes = mapOf(
            AgeSignalsError.API_NOT_AVAILABLE to "-1",
            AgeSignalsError.PLAY_STORE_NOT_FOUND to "-2",
            AgeSignalsError.NETWORK_ERROR to "-3",
            AgeSignalsError.PLAY_SERVICES_NOT_FOUND to "-4",
            AgeSignalsError.CANNOT_BIND_TO_SERVICE to "-5",
            AgeSignalsError.PLAY_STORE_VERSION_OUTDATED to "-6",
            AgeSignalsError.PLAY_SERVICES_VERSION_OUTDATED to "-7",
            AgeSignalsError.CLIENT_TRANSIENT_ERROR to "-8",
            AgeSignalsError.APP_NOT_OWNED to "-9",
            AgeSignalsError.SDK_VERSION_OUTDATED to "-10",
            AgeSignalsError.DEVICE_NOT_SUPPORTED to "-11",
            AgeSignalsError.INTERNAL_ERROR to "-100",
            AgeSignalsError.UNKNOWN to "unknown",
            AgeSignalsError.TIMEOUT to "timeout",
        )
        assertEquals(AgeSignalsError.entries.toSet(), expectedCodes.keys)

        expectedCodes.forEach { (error, code) ->
            Step.entries.forEach { step ->
                clearInvocations(pixel)

                pixels.fireCheck(Failed(step, error))

                verifyCountAndDaily(
                    CHECK_COUNT,
                    CHECK_DAILY,
                    mapOf("result" to "failure", "error_code" to code, PETAL to PETAL_RANDOMIZE),
                    "m_age_signals_check_daily:failure:$code",
                )
            }
        }
    }

    @Test
    fun whenThePromptIsShownThenThePromptPixelFiresWithoutData() {
        pixels.firePromptShown()

        verifyCountAndDaily(PROMPT_SHOWN_COUNT, PROMPT_SHOWN_DAILY, mapOf(PETAL to PETAL_RANDOMIZE), null)
    }

    @Test
    fun whenAnyOutcomeIsReportedThenNoParameterValueNamesAnOutcomeOrAStep() {
        val outcomes = listOf(NotApplicable, NotShared, VerificationRequired, Shared, UnknownStatus) +
            AgeSignalsError.entries.flatMap { error -> Step.entries.map { Failed(it, error) } }
        val forbidden = (
            outcomes.map { it.javaClass.simpleName } +
                Step.entries.map { it.name } +
                AgeSignalsScenario.entries.map { it.name }
            ).map { it.lowercase() }.toSet()

        outcomes.forEach { pixels.fireCheck(it) }
        pixels.firePromptShown()

        val parameters = argumentCaptor<Map<String, String>>()
        verify(pixel, atLeastOnce()).fire(any<Pixel.PixelName>(), parameters.capture(), any(), any())
        val values = parameters.allValues.flatMap { it.values }.map { it.lowercase() }.toSet()
        assertTrue("leaked: ${values intersect forbidden}", (values intersect forbidden).isEmpty())
    }

    private fun verifyCountAndDaily(
        count: AgeSignalsPixelName,
        daily: AgeSignalsPixelName,
        parameters: Map<String, String>,
        dailyTag: String?,
    ) {
        verify(pixel).fire(eq(count), eq(parameters), any(), eq(Count))
        verify(pixel).fire(eq(daily), eq(parameters), any(), eq(Daily(dailyTag)))
        verifyNoMoreInteractions(pixel)
    }
}
