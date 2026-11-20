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

import androidx.annotation.StringRes
import com.duckduckgo.agesignals.impl.AgeSignalsError.APP_NOT_OWNED
import com.duckduckgo.agesignals.impl.AgeSignalsError.INTERNAL_ERROR
import com.duckduckgo.agesignals.impl.AgeSignalsError.NETWORK_ERROR
import com.duckduckgo.agesignals.impl.Step.APPLICABLE_FEATURES
import com.duckduckgo.agesignals.impl.Step.CHECK_SIGNALS
import com.duckduckgo.agesignals.impl.Step.REQUEST_ACCESS
import com.google.android.play.agesignals.AgeSignalsAccessResult
import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.AgeSignalsResult
import com.google.android.play.agesignals.ApplicableAgeFeaturesResult
import com.google.android.play.agesignals.model.AgeSignalsStatus
import com.google.android.play.agesignals.model.ApplicableAgeFeature
import com.google.android.play.agesignals.testing.FakeAgeSignalsManager

internal data class FakeResponses(
    val mandatorySharing: Boolean = true,
    val accessStatus: Int = AgeSignalsStatus.SHARED,
    val failure: Pair<Step, AgeSignalsError>? = null,
)

internal enum class AgeSignalsScenario(@StringRes val title: Int, val fake: FakeResponses?) {
    REAL_PLAY(R.string.ageSignalsScenarioRealPlay, null),
    NOT_APPLICABLE(R.string.ageSignalsScenarioNotApplicable, FakeResponses(mandatorySharing = false)),
    NOT_SHARED(R.string.ageSignalsScenarioNotShared, FakeResponses(accessStatus = AgeSignalsStatus.NOT_SHARED)),
    VERIFICATION_REQUIRED(R.string.ageSignalsScenarioVerificationRequired, FakeResponses(accessStatus = AgeSignalsStatus.VERIFICATION_REQUIRED)),
    SHARED(R.string.ageSignalsScenarioShared, FakeResponses()),
    UNKNOWN_STATUS(R.string.ageSignalsScenarioUnknownStatus, FakeResponses(accessStatus = AgeSignalsStatus.UNSPECIFIED)),
    APP_NOT_OWNED_AT_FEATURES(R.string.ageSignalsScenarioAppNotOwnedAtFeatures, FakeResponses(failure = APPLICABLE_FEATURES to APP_NOT_OWNED)),
    NETWORK_ERROR_AT_FEATURES(R.string.ageSignalsScenarioNetworkErrorAtFeatures, FakeResponses(failure = APPLICABLE_FEATURES to NETWORK_ERROR)),
    INTERNAL_ERROR_AT_FEATURES(R.string.ageSignalsScenarioInternalErrorAtFeatures, FakeResponses(failure = APPLICABLE_FEATURES to INTERNAL_ERROR)),
    APP_NOT_OWNED_AT_ACCESS(R.string.ageSignalsScenarioAppNotOwnedAtAccess, FakeResponses(failure = REQUEST_ACCESS to APP_NOT_OWNED)),
    NETWORK_ERROR_AT_ACCESS(R.string.ageSignalsScenarioNetworkErrorAtAccess, FakeResponses(failure = REQUEST_ACCESS to NETWORK_ERROR)),
    INTERNAL_ERROR_AT_ACCESS(R.string.ageSignalsScenarioInternalErrorAtAccess, FakeResponses(failure = REQUEST_ACCESS to INTERNAL_ERROR)),
    APP_NOT_OWNED_AT_CHECK(R.string.ageSignalsScenarioAppNotOwnedAtCheck, FakeResponses(failure = CHECK_SIGNALS to APP_NOT_OWNED)),
    NETWORK_ERROR_AT_CHECK(R.string.ageSignalsScenarioNetworkErrorAtCheck, FakeResponses(failure = CHECK_SIGNALS to NETWORK_ERROR)),
    INTERNAL_ERROR_AT_CHECK(R.string.ageSignalsScenarioInternalErrorAtCheck, FakeResponses(failure = CHECK_SIGNALS to INTERNAL_ERROR)),
}

internal fun FakeResponses.toFakeManager(): FakeAgeSignalsManager = FakeAgeSignalsManager().apply {
    val featuresFailure = exceptionAt(APPLICABLE_FEATURES)
    if (featuresFailure != null) {
        setNextApplicableAgeFeaturesException(featuresFailure)
    } else {
        val features = if (mandatorySharing) setOf(ApplicableAgeFeature.MANDATORY_SHARING) else emptySet()
        setNextApplicableAgeFeaturesResult(ApplicableAgeFeaturesResult.builder().setFeatures(features).build())
    }

    val accessFailure = exceptionAt(REQUEST_ACCESS)
    if (accessFailure != null) {
        setNextRequestAgeSignalsAccessException(accessFailure)
    } else {
        setNextAgeSignalsAccessResult(AgeSignalsAccessResult.builder().setAgeSignalsStatus(accessStatus).build())
    }

    val checkFailure = exceptionAt(CHECK_SIGNALS)
    if (checkFailure != null) {
        setNextAgeSignalsException(checkFailure)
    } else {
        setNextAgeSignalsResult(AgeSignalsResult.builder().build())
    }
}

private fun FakeResponses.exceptionAt(step: Step): AgeSignalsException? =
    failure?.takeIf { it.first == step }?.let { AgeSignalsException(it.second.code) }
