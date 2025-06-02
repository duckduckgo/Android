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
    fun billingPeriodParsesDaysCorrectly() {
        val phase = PricingPhase(formattedPrice = "Free", billingPeriod = "P10D")
        assertEquals(10, phase.getBillingPeriodInDays())
    }

    @Test
    fun billingPeriodParsesWeeksCorrectly() {
        val phase = PricingPhase(formattedPrice = "Free", billingPeriod = "P2W")
        assertEquals(14, phase.getBillingPeriodInDays())
    }

    @Test
    fun billingPeriodParsesMonthsCorrectly() {
        val phase = PricingPhase(formattedPrice = "Free", billingPeriod = "P2M")
        assertEquals(60, phase.getBillingPeriodInDays())
    }

    @Test
    fun billingPeriodParsesYearsCorrectly() {
        val phase = PricingPhase(formattedPrice = "Free", billingPeriod = "P1Y")
        assertEquals(365, phase.getBillingPeriodInDays())
    }

    @Test
    fun billingPeriodParsesMixedPeriodCorrectly() {
        val phase = PricingPhase(formattedPrice = "Free", billingPeriod = "P1Y2M10D")
        val expectedDays = 1 * 365 + 2 * 30 + 10 // 365 + 60 + 10 = 435
        assertEquals(expectedDays, phase.getBillingPeriodInDays())
    }

    @Test
    fun billingPeriodEmptyReturnsNull() {
        val phase = PricingPhase(formattedPrice = "$0", billingPeriod = "")
        assertNull(phase.getBillingPeriodInDays())
    }

    @Test
    fun billingPeriodReturnsNullForInvalidFormat() {
        val phase = PricingPhase(formattedPrice = "Free", billingPeriod = "INVALID")
        assertNull(phase.getBillingPeriodInDays())
    }
}
