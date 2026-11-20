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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeSignalsParserTest {

    @Test
    fun whenFeaturesContainMandatorySharingThenSharingIsMandatory() {
        assertTrue(AgeSignalsParser.isSharingMandatory(features(1)))
    }

    @Test
    fun whenFeaturesAreEmptyThenSharingIsNotMandatory() {
        assertFalse(AgeSignalsParser.isSharingMandatory(features()))
    }

    @Test
    fun whenFeaturesContainOnlyUnknownFeaturesThenSharingIsNotMandatory() {
        assertFalse(AgeSignalsParser.isSharingMandatory(features(2, 99)))
    }

    @Test
    fun whenStatusIsSharedThenOutcomeIsShared() {
        assertEquals(Shared, AgeSignalsParser.parseAccess(access(1)))
    }

    @Test
    fun whenStatusIsNotSharedThenOutcomeIsNotShared() {
        assertEquals(NotShared, AgeSignalsParser.parseAccess(access(2)))
    }

    @Test
    fun whenStatusIsVerificationRequiredThenOutcomeIsVerificationRequired() {
        assertEquals(VerificationRequired, AgeSignalsParser.parseAccess(access(3)))
    }

    @Test
    fun whenStatusIsUnspecifiedThenOutcomeIsUnknownStatus() {
        assertEquals(UnknownStatus, AgeSignalsParser.parseAccess(access(0)))
    }

    @Test
    fun whenStatusIsNullThenOutcomeIsUnknownStatus() {
        assertEquals(UnknownStatus, AgeSignalsParser.parseAccess(access(null)))
    }

    @Test
    fun whenStatusIsAnUnknownIntThenOutcomeIsUnknownStatus() {
        assertEquals(UnknownStatus, AgeSignalsParser.parseAccess(access(42)))
    }

    @Test
    fun whenExceptionHasAKnownCodeThenFailureCarriesTheMatchingError() {
        val expected = mapOf(
            -1 to AgeSignalsError.API_NOT_AVAILABLE,
            -2 to AgeSignalsError.PLAY_STORE_NOT_FOUND,
            -3 to AgeSignalsError.NETWORK_ERROR,
            -4 to AgeSignalsError.PLAY_SERVICES_NOT_FOUND,
            -5 to AgeSignalsError.CANNOT_BIND_TO_SERVICE,
            -6 to AgeSignalsError.PLAY_STORE_VERSION_OUTDATED,
            -7 to AgeSignalsError.PLAY_SERVICES_VERSION_OUTDATED,
            -8 to AgeSignalsError.CLIENT_TRANSIENT_ERROR,
            -9 to AgeSignalsError.APP_NOT_OWNED,
            -10 to AgeSignalsError.SDK_VERSION_OUTDATED,
            -11 to AgeSignalsError.DEVICE_NOT_SUPPORTED,
            -100 to AgeSignalsError.INTERNAL_ERROR,
        )

        expected.forEach { (code, error) ->
            assertEquals(Failed(Step.REQUEST_ACCESS, error), AgeSignalsParser.parseFailure(Step.REQUEST_ACCESS, AgeSignalsException(code)))
        }
    }

    @Test
    fun whenExceptionHasAnUnknownCodeThenFailureCarriesUnknown() {
        assertEquals(
            Failed(Step.CHECK_SIGNALS, AgeSignalsError.UNKNOWN),
            AgeSignalsParser.parseFailure(Step.CHECK_SIGNALS, AgeSignalsException(-12345)),
        )
    }

    @Test
    fun whenExceptionIsParsedThenFailureKeepsTheStep() {
        Step.entries.forEach { step ->
            assertEquals(step, AgeSignalsParser.parseFailure(step, AgeSignalsException(-3)).step)
        }
    }

    private fun features(vararg features: Int): ApplicableAgeFeaturesResult =
        ApplicableAgeFeaturesResult.builder().setFeatures(features.toSet()).build()

    private fun access(status: Int?): AgeSignalsAccessResult =
        AgeSignalsAccessResult.builder().setAgeSignalsStatus(status).build()
}
