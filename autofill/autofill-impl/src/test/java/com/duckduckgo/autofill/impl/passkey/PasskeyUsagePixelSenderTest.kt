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

package com.duckduckgo.autofill.impl.passkey

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autofill.impl.passkey.PasskeyUsageError.NotAllowedError
import com.duckduckgo.autofill.impl.passkey.PasskeyUsagePixelSender.Companion.PARAM_ERROR
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_CREATE_FAILURE_COUNT
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_CREATE_FAILURE_DAILY
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_CREATE_SUCCESS_COUNT
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_CREATE_SUCCESS_DAILY
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_UNKNOWN_EVENT_COUNT
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_UNKNOWN_EVENT_DAILY
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_USE_FAILURE_COUNT
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_USE_FAILURE_DAILY
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_USE_SUCCESS_COUNT
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_USE_SUCCESS_DAILY
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PasskeyUsagePixelSenderTest {

    private val pixel: Pixel = mock()
    private val testee = PasskeyUsagePixelSender(pixel)

    @Test
    fun whenFirePasskeyUsedThenFiresUseSuccessCountAndDaily() {
        testee.firePasskeyUsed()

        verifyCountAndDaily(AUTOFILL_PASSKEY_USE_SUCCESS_COUNT, AUTOFILL_PASSKEY_USE_SUCCESS_DAILY)
    }

    @Test
    fun whenFirePasskeyCreatedThenFiresCreateSuccessCountAndDaily() {
        testee.firePasskeyCreated()

        verifyCountAndDaily(AUTOFILL_PASSKEY_CREATE_SUCCESS_COUNT, AUTOFILL_PASSKEY_CREATE_SUCCESS_DAILY)
    }

    @Test
    fun whenFireUseFailedThenFiresUseFailureCountAndDailyWithError() {
        testee.fireUseFailed(NotAllowedError)

        verifyCountAndDaily(
            count = AUTOFILL_PASSKEY_USE_FAILURE_COUNT,
            daily = AUTOFILL_PASSKEY_USE_FAILURE_DAILY,
            parameters = mapOf(PARAM_ERROR to NotAllowedError.name),
            dailyTag = "${AUTOFILL_PASSKEY_USE_FAILURE_DAILY.pixelName}:error:${NotAllowedError.name}",
        )
    }

    @Test
    fun whenFireCreateFailedThenFiresCreateFailureCountAndDailyWithError() {
        testee.fireCreateFailed(PasskeyUsageError.InvalidStateError)

        verifyCountAndDaily(
            count = AUTOFILL_PASSKEY_CREATE_FAILURE_COUNT,
            daily = AUTOFILL_PASSKEY_CREATE_FAILURE_DAILY,
            parameters = mapOf(PARAM_ERROR to PasskeyUsageError.InvalidStateError.name),
            dailyTag = "${AUTOFILL_PASSKEY_CREATE_FAILURE_DAILY.pixelName}:error:${PasskeyUsageError.InvalidStateError.name}",
        )
    }

    @Test
    fun whenFireUnknownEventThenFiresUnknownEventCountAndDaily() {
        testee.fireUnknownEvent()

        verifyCountAndDaily(AUTOFILL_PASSKEY_UNKNOWN_EVENT_COUNT, AUTOFILL_PASSKEY_UNKNOWN_EVENT_DAILY)
    }

    @Test
    fun whenFireUseFailedWithUnknownErrorThenErrorParamIsAllowlistedName() {
        testee.fireUseFailed(PasskeyUsageError.UnknownError)

        verifyCountAndDaily(
            count = AUTOFILL_PASSKEY_USE_FAILURE_COUNT,
            daily = AUTOFILL_PASSKEY_USE_FAILURE_DAILY,
            parameters = mapOf(PARAM_ERROR to PasskeyUsageError.UnknownError.name),
            dailyTag = "${AUTOFILL_PASSKEY_USE_FAILURE_DAILY.pixelName}:error:${PasskeyUsageError.UnknownError.name}",
        )
    }

    @Test
    fun whenFireUseFailedWithDifferentErrorsThenDailyTagsDiffer() {
        testee.fireUseFailed(NotAllowedError)
        testee.fireUseFailed(PasskeyUsageError.UnknownError)

        verify(pixel).fire(
            eq(AUTOFILL_PASSKEY_USE_FAILURE_DAILY),
            eq(mapOf(PARAM_ERROR to NotAllowedError.name)),
            any(),
            eq(Daily("${AUTOFILL_PASSKEY_USE_FAILURE_DAILY.pixelName}:error:${NotAllowedError.name}")),
        )
        verify(pixel).fire(
            eq(AUTOFILL_PASSKEY_USE_FAILURE_DAILY),
            eq(mapOf(PARAM_ERROR to PasskeyUsageError.UnknownError.name)),
            any(),
            eq(Daily("${AUTOFILL_PASSKEY_USE_FAILURE_DAILY.pixelName}:error:${PasskeyUsageError.UnknownError.name}")),
        )
    }

    private fun verifyCountAndDaily(
        count: AutofillPixelNames,
        daily: AutofillPixelNames,
        parameters: Map<String, String> = emptyMap(),
        dailyTag: String? = null,
    ) {
        verify(pixel).fire(eq(count), eq(parameters), any(), eq(Count))
        verify(pixel).fire(eq(daily), eq(parameters), any(), eq(Daily(dailyTag)))
    }
}
