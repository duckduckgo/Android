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

package com.duckduckgo.duckchat.impl.models

import com.duckduckgo.duckchat.impl.models.ReasoningEffort.HIGH
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.LOW
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.MEDIUM
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.MINIMAL
import com.duckduckgo.duckchat.impl.models.ReasoningEffort.NONE
import com.duckduckgo.duckchat.impl.models.ReasoningMode.EXTENDED_REASONING
import com.duckduckgo.duckchat.impl.models.ReasoningMode.FAST
import com.duckduckgo.duckchat.impl.models.ReasoningMode.REASONING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReasoningResolverTest {

    @Test
    fun whenSupportedHasNoneAndLowThenAvailableModesAreFastAndReasoning() {
        val result = ReasoningResolver.availableModes(listOf(NONE, LOW))
        assertEquals(
            listOf(
                AvailableReasoningMode(FAST, NONE),
                AvailableReasoningMode(REASONING, LOW),
            ),
            result,
        )
    }

    @Test
    fun whenSupportedHasNoneLowMediumThenAllThreeModesAvailableWithMediumForExtended() {
        val result = ReasoningResolver.availableModes(listOf(NONE, LOW, MEDIUM))
        assertEquals(
            listOf(
                AvailableReasoningMode(FAST, NONE),
                AvailableReasoningMode(REASONING, LOW),
                AvailableReasoningMode(EXTENDED_REASONING, MEDIUM),
            ),
            result,
        )
    }

    @Test
    fun whenSupportedHasAllFourThenExtendedPrefersHighOverMedium() {
        val result = ReasoningResolver.availableModes(listOf(NONE, LOW, MEDIUM, HIGH))
        assertEquals(
            listOf(
                AvailableReasoningMode(FAST, NONE),
                AvailableReasoningMode(REASONING, LOW),
                AvailableReasoningMode(EXTENDED_REASONING, HIGH),
            ),
            result,
        )
    }

    @Test
    fun whenSupportedIsOutOfOrderThenUiOrderIsStillFastReasoningExtended() {
        val result = ReasoningResolver.availableModes(listOf(MEDIUM, LOW, NONE))
        assertEquals(
            listOf(
                AvailableReasoningMode(FAST, NONE),
                AvailableReasoningMode(REASONING, LOW),
                AvailableReasoningMode(EXTENDED_REASONING, MEDIUM),
            ),
            result,
        )
    }

    @Test
    fun whenSupportedHasMinimalAndMediumThenFastUsesMinimalAndReasoningOmitted() {
        val result = ReasoningResolver.availableModes(listOf(MINIMAL, MEDIUM))
        assertEquals(
            listOf(
                AvailableReasoningMode(FAST, MINIMAL),
                AvailableReasoningMode(EXTENDED_REASONING, MEDIUM),
            ),
            result,
        )
    }

    @Test
    fun whenSupportedHasOnlyLowThenOnlyReasoningAvailable() {
        val result = ReasoningResolver.availableModes(listOf(LOW))
        assertEquals(listOf(AvailableReasoningMode(REASONING, LOW)), result)
    }

    @Test
    fun whenSupportedIsEmptyThenAvailableModesEmpty() {
        assertEquals(emptyList<AvailableReasoningMode>(), ReasoningResolver.availableModes(emptyList()))
    }

    @Test
    fun whenAvailableEmptyThenResolveReturnsNull() {
        assertNull(ReasoningResolver.resolveMode(persisted = FAST, available = emptyList()))
    }

    @Test
    fun whenPersistedNullThenResolveReturnsFirstAvailable() {
        val available = ReasoningResolver.availableModes(listOf(NONE, LOW))
        assertEquals(FAST, ReasoningResolver.resolveMode(persisted = null, available = available))
    }

    @Test
    fun whenPersistedSupportedThenResolveReturnsPersisted() {
        val available = ReasoningResolver.availableModes(listOf(NONE, LOW))
        assertEquals(REASONING, ReasoningResolver.resolveMode(persisted = REASONING, available = available))
    }

    @Test
    fun whenPersistedStaleThenResolveReturnsFirstAvailable() {
        val available = ReasoningResolver.availableModes(listOf(LOW))
        assertEquals(REASONING, ReasoningResolver.resolveMode(persisted = FAST, available = available))
    }

    @Test
    fun whenEffortForKnownModeThenReturnsPriorityEffort() {
        val available = ReasoningResolver.availableModes(listOf(NONE, MEDIUM, HIGH))
        assertEquals(HIGH, ReasoningResolver.effortFor(EXTENDED_REASONING, available))
    }

    @Test
    fun whenEffortForUnsupportedModeThenReturnsNull() {
        val available = ReasoningResolver.availableModes(listOf(LOW))
        assertNull(ReasoningResolver.effortFor(FAST, available))
    }

    @Test
    fun whenEffortForNullModeThenReturnsNull() {
        val available = ReasoningResolver.availableModes(listOf(LOW))
        assertNull(ReasoningResolver.effortFor(null, available))
    }

    @Test
    fun whenReasoningEffortFromKnownRawThenReturnsEnum() {
        assertEquals(LOW, ReasoningEffort.from("low"))
        assertEquals(MEDIUM, ReasoningEffort.from("medium"))
        assertEquals(HIGH, ReasoningEffort.from("high"))
        assertEquals(NONE, ReasoningEffort.from("none"))
        assertEquals(MINIMAL, ReasoningEffort.from("minimal"))
    }

    @Test
    fun whenReasoningEffortFromUnknownRawThenReturnsNull() {
        assertNull(ReasoningEffort.from("garbage"))
        assertNull(ReasoningEffort.from(null))
    }

    @Test
    fun whenReasoningModeFromKnownRawThenReturnsEnum() {
        assertEquals(FAST, ReasoningMode.from("fast"))
        assertEquals(REASONING, ReasoningMode.from("reasoning"))
        assertEquals(EXTENDED_REASONING, ReasoningMode.from("extended_reasoning"))
    }

    @Test
    fun whenReasoningModeFromUnknownRawThenReturnsNull() {
        assertNull(ReasoningMode.from("EXTENDED"))
        assertNull(ReasoningMode.from(null))
    }
}
