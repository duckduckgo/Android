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
import javax.inject.Inject

class PasskeyUsagePixelSender @Inject constructor(
    private val pixel: Pixel,
) {

    fun firePasskeyUsed() {
        fireCountAndDaily(
            count = AUTOFILL_PASSKEY_USE_SUCCESS_COUNT,
            daily = AUTOFILL_PASSKEY_USE_SUCCESS_DAILY,
        )
    }

    fun fireUseFailed(error: PasskeyUsageError) {
        fireCountAndDaily(
            count = AUTOFILL_PASSKEY_USE_FAILURE_COUNT,
            daily = AUTOFILL_PASSKEY_USE_FAILURE_DAILY,
            parameters = errorParams(error),
            dailyTag = dailyTagForError(AUTOFILL_PASSKEY_USE_FAILURE_DAILY, error),
        )
    }

    fun firePasskeyCreated() {
        fireCountAndDaily(
            count = AUTOFILL_PASSKEY_CREATE_SUCCESS_COUNT,
            daily = AUTOFILL_PASSKEY_CREATE_SUCCESS_DAILY,
        )
    }

    fun fireCreateFailed(error: PasskeyUsageError) {
        fireCountAndDaily(
            count = AUTOFILL_PASSKEY_CREATE_FAILURE_COUNT,
            daily = AUTOFILL_PASSKEY_CREATE_FAILURE_DAILY,
            parameters = errorParams(error),
            dailyTag = dailyTagForError(AUTOFILL_PASSKEY_CREATE_FAILURE_DAILY, error),
        )
    }

    fun fireUnknownEvent() {
        fireCountAndDaily(
            count = AUTOFILL_PASSKEY_UNKNOWN_EVENT_COUNT,
            daily = AUTOFILL_PASSKEY_UNKNOWN_EVENT_DAILY,
        )
    }

    private fun fireCountAndDaily(
        count: AutofillPixelNames,
        daily: AutofillPixelNames,
        parameters: Map<String, String> = emptyMap(),
        dailyTag: String? = null,
    ) {
        pixel.fire(count, parameters)
        pixel.fire(daily, parameters, type = Pixel.PixelType.Daily(dailyTag))
    }

    private fun dailyTagForError(
        daily: AutofillPixelNames,
        error: PasskeyUsageError,
    ) = "${daily.pixelName}:error:${error.name}"

    private fun errorParams(error: PasskeyUsageError) = mapOf(PARAM_ERROR to error.name)

    companion object {
        const val PARAM_ERROR = "error"
    }
}
