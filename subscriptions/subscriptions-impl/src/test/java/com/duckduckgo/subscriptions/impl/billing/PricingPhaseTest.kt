/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.billing

import com.duckduckgo.subscriptions.impl.PricingPhase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class PricingPhaseTest {
    @Test
    fun returnsCorrectDaysForDays() {
        val phase = PricingPhase("Free", "P10D")
        assertEquals(10, phase.getBillingPeriodInDays())
    }

    @Test
    fun returnsCorrectDaysForWeeks() {
        val phase = PricingPhase("Free", "P2W")
        assertEquals(14, phase.getBillingPeriodInDays())
    }

    @Test
    fun returnsCorrectDaysForMonths() {
        val phase = PricingPhase("Free", "P3M")
        assertEquals(90, phase.getBillingPeriodInDays())
    }

    @Test
    fun returnsCorrectDaysForYears() {
        val phase = PricingPhase("Free", "P1Y")
        assertEquals(365, phase.getBillingPeriodInDays())
    }

    @Test
    fun returnsNullForInvalidFormat() {
        val phase = PricingPhase("Free", "XYZ")
        assertNull(phase.getBillingPeriodInDays())
    }

    @Test
    fun returnsNullForMissingNumber() {
        val phase = PricingPhase("Free", "PM")
        assertNull(phase.getBillingPeriodInDays())
    }

    @Test
    fun returnsNullForEmptyString() {
        val phase = PricingPhase("Free", "")
        assertNull(phase.getBillingPeriodInDays())
    }

    @Test
    fun returnsNullForMixedPeriods() {
        val phase = PricingPhase("Free", "P1M2W")
        assertNull(phase.getBillingPeriodInDays()) // Not supported in simplified version
    }
}
