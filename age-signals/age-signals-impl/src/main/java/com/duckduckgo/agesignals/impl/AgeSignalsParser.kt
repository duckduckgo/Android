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
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.NotShared
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.Shared
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.UnknownStatus
import com.duckduckgo.agesignals.impl.AgeSignalsOutcome.VerificationRequired
import com.google.android.play.agesignals.AgeSignalsAccessResult
import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.ApplicableAgeFeaturesResult
import com.google.android.play.agesignals.model.AgeSignalsStatus
import com.google.android.play.agesignals.model.ApplicableAgeFeature

internal object AgeSignalsParser {

    fun isSharingMandatory(result: ApplicableAgeFeaturesResult): Boolean =
        ApplicableAgeFeature.MANDATORY_SHARING in result.features()

    /** [Shared] means access was granted and the caller still has to run the check step. */
    fun parseAccess(result: AgeSignalsAccessResult): AgeSignalsOutcome = when (result.ageSignalsStatus()) {
        AgeSignalsStatus.SHARED -> Shared
        AgeSignalsStatus.NOT_SHARED -> NotShared
        AgeSignalsStatus.VERIFICATION_REQUIRED -> VerificationRequired
        else -> UnknownStatus
    }

    fun parseFailure(step: Step, exception: AgeSignalsException): Failed =
        Failed(step, AgeSignalsError.entries.firstOrNull { it.code == exception.errorCode } ?: AgeSignalsError.UNKNOWN)
}
