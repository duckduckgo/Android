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

package com.duckduckgo.pir.impl.brokers

import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.INVALID_STEP
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.MISSING_STEP
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.UNSUPPORTED_ACTION
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdateFailureReason.UNSUPPORTED_STEP
import com.duckduckgo.pir.impl.di.PirModule
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealBrokerStepsValidatorTest {

    private val testee = RealBrokerStepsValidator(PirModule().providePirMoshi(Moshi.Builder().build()))

    @Test
    fun whenScanAndOptOutStepsAreSupportedThenReturnsNull() {
        assertNull(testee.validate(listOf(scanStep(NAVIGATE), optOutStep(NAVIGATE, EMAIL_CONFIRMATION))))
    }

    @Test
    fun whenOptOutStepIsParentSiteOptOutWithoutActionsThenReturnsNull() {
        val parentSiteOptOut = """{"stepType":"optOut","optOutType":"parentSiteOptOut","actions":[]}"""

        assertNull(testee.validate(listOf(scanStep(NAVIGATE), parentSiteOptOut)))
    }

    @Test
    fun whenStepTypeIsUnknownThenReturnsUnsupportedStep() {
        val unknownStep = """{"stepType":"verify","actions":[]}"""

        assertEquals(UNSUPPORTED_STEP, testee.validate(listOf(scanStep(NAVIGATE), optOutStep(NAVIGATE), unknownStep)))
    }

    @Test
    fun whenStepTypeIsMissingThenReturnsUnsupportedStep() {
        val stepWithoutType = """{"scanType":"templatedUrl","actions":[]}"""

        assertEquals(UNSUPPORTED_STEP, testee.validate(listOf(stepWithoutType, optOutStep(NAVIGATE))))
    }

    @Test
    fun whenScanStepHasUnknownActionThenReturnsUnsupportedAction() {
        assertEquals(UNSUPPORTED_ACTION, testee.validate(listOf(scanStep(NAVIGATE, UNKNOWN_ACTION), optOutStep(NAVIGATE))))
    }

    @Test
    fun whenOptOutStepHasUnknownActionThenReturnsUnsupportedAction() {
        assertEquals(UNSUPPORTED_ACTION, testee.validate(listOf(scanStep(NAVIGATE), optOutStep(NAVIGATE, UNKNOWN_ACTION))))
    }

    @Test
    fun whenConditionActionContainsUnknownActionThenReturnsUnsupportedAction() {
        val condition = """{"actionType":"condition","id":"c1","expectations":[],"actions":[$UNKNOWN_ACTION]}"""

        assertEquals(UNSUPPORTED_ACTION, testee.validate(listOf(scanStep(NAVIGATE), optOutStep(condition))))
    }

    @Test
    fun whenExpectationActionContainsUnknownActionThenReturnsUnsupportedAction() {
        val expectation = """{"actionType":"expectation","id":"e1","expectations":[],"actions":[$UNKNOWN_ACTION]}"""

        assertEquals(UNSUPPORTED_ACTION, testee.validate(listOf(scanStep(expectation), optOutStep(NAVIGATE))))
    }

    @Test
    fun whenNestedActionsAreSupportedThenReturnsNull() {
        val condition = """{"actionType":"condition","id":"c1","expectations":[],"actions":[$NAVIGATE]}"""

        assertNull(testee.validate(listOf(scanStep(NAVIGATE), optOutStep(condition))))
    }

    @Test
    fun whenSupportedActionLacksRequiredFieldThenReturnsInvalidStep() {
        val navigateWithoutUrl = """{"actionType":"navigate","id":"n1"}"""

        assertEquals(INVALID_STEP, testee.validate(listOf(scanStep(navigateWithoutUrl), optOutStep(NAVIGATE))))
    }

    @Test
    fun whenActionLacksActionTypeThenReturnsInvalidStep() {
        val actionWithoutType = """{"id":"n1","url":"https://broker.com"}"""

        assertEquals(INVALID_STEP, testee.validate(listOf(scanStep(actionWithoutType), optOutStep(NAVIGATE))))
    }

    @Test
    fun whenStepIsNotJsonObjectThenReturnsInvalidStep() {
        assertEquals(INVALID_STEP, testee.validate(listOf("not json", optOutStep(NAVIGATE))))
    }

    @Test
    fun whenOptOutStepIsMissingThenReturnsMissingStep() {
        assertEquals(MISSING_STEP, testee.validate(listOf(scanStep(NAVIGATE))))
    }

    @Test
    fun whenScanStepIsMissingThenReturnsMissingStep() {
        assertEquals(MISSING_STEP, testee.validate(listOf(optOutStep(NAVIGATE))))
    }

    @Test
    fun whenThereAreNoStepsThenReturnsMissingStep() {
        assertEquals(MISSING_STEP, testee.validate(emptyList()))
    }

    private fun scanStep(vararg actions: String) =
        """{"stepType":"scan","scanType":"templatedUrl","actions":[${actions.joinToString(",")}]}"""

    private fun optOutStep(vararg actions: String) =
        """{"stepType":"optOut","optOutType":"formOptOut","actions":[${actions.joinToString(",")}]}"""

    companion object {
        private const val NAVIGATE = """{"actionType":"navigate","id":"n1","url":"https://broker.com/search"}"""
        private const val EMAIL_CONFIRMATION = """{"actionType":"emailConfirmation","id":"ec1","pollingTime":"30"}"""
        private const val UNKNOWN_ACTION = """{"actionType":"newAction","id":"x1"}"""
    }
}
