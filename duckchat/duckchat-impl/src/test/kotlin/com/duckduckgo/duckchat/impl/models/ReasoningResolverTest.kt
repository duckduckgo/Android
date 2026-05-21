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

    // ---- per-effort access integration ----

    @Test
    fun whenEffortAccessMissingForSupportedEffortThenModeIsAccessibleByDefault() {
        // No per-effort gating data → modes inherit model access, surfaced as `access=null` and `isAccessible=true`.
        val result = ReasoningResolver.availableModes(supported = listOf(NONE, LOW), effortAccess = emptyList())
        assertEquals(listOf(AvailableReasoningMode(FAST, NONE), AvailableReasoningMode(REASONING, LOW)), result)
        assertEquals(true, result.all { it.isAccessible })
    }

    @Test
    fun whenAllEffortsAccessibleThenAccessIsCarriedThroughAndModesAccessible() {
        val access = listOf(
            ReasoningEffortAccess(NONE, listOf("free", "plus", "pro"), isAccessible = true),
            ReasoningEffortAccess(LOW, listOf("free", "plus", "pro"), isAccessible = true),
        )
        val result = ReasoningResolver.availableModes(listOf(NONE, LOW), access)
        assertEquals(access[0], result[0].access)
        assertEquals(true, result[0].isAccessible)
        assertEquals(access[1], result[1].access)
        assertEquals(true, result[1].isAccessible)
    }

    @Test
    fun whenPreferredCandidateGatedAndFallbackAccessibleThenFallbackUsed() {
        // EXTENDED_REASONING candidates = [HIGH, MEDIUM]. HIGH gated to PRO, MEDIUM accessible → MEDIUM chosen.
        val access = listOf(
            ReasoningEffortAccess(HIGH, listOf("pro"), isAccessible = false),
            ReasoningEffortAccess(MEDIUM, listOf("free", "plus", "pro"), isAccessible = true),
        )
        val result = ReasoningResolver.availableModes(listOf(MEDIUM, HIGH), access)
        val extended = result.single { it.mode == EXTENDED_REASONING }
        assertEquals(MEDIUM, extended.effort)
        assertEquals(true, extended.isAccessible)
    }

    @Test
    fun whenAllCandidatesGatedThenFirstSupportedReturnedAsGatedRow() {
        // Both candidates gated → preferred (HIGH) returned with its gated access entry; row shows for upsell.
        val highAccess = ReasoningEffortAccess(HIGH, listOf("pro"), isAccessible = false)
        val mediumAccess = ReasoningEffortAccess(MEDIUM, listOf("pro"), isAccessible = false)
        val result = ReasoningResolver.availableModes(listOf(MEDIUM, HIGH), listOf(highAccess, mediumAccess))
        val extended = result.single { it.mode == EXTENDED_REASONING }
        assertEquals(HIGH, extended.effort)
        assertEquals(highAccess, extended.access)
        assertEquals(false, extended.isAccessible)
    }

    @Test
    fun whenAllAvailableModesGatedThenResolveReturnsNull() {
        // Model is accessible but every reasoning effort is gated to a higher tier → no fallback mode to auto-select.
        val available = listOf(
            AvailableReasoningMode(FAST, NONE, ReasoningEffortAccess(NONE, listOf("pro"), isAccessible = false)),
            AvailableReasoningMode(REASONING, LOW, ReasoningEffortAccess(LOW, listOf("pro"), isAccessible = false)),
        )
        assertNull(ReasoningResolver.resolveMode(persisted = FAST, available = available))
        assertNull(ReasoningResolver.resolveMode(persisted = null, available = available))
    }

    @Test
    fun whenPersistedModeIsGatedAndOthersAccessibleThenResolveFallsBackToFirstAccessible() {
        val available = listOf(
            AvailableReasoningMode(FAST, NONE, ReasoningEffortAccess(NONE, listOf("pro"), isAccessible = false)),
            AvailableReasoningMode(REASONING, LOW, ReasoningEffortAccess(LOW, listOf("free", "plus", "pro"), isAccessible = true)),
        )
        assertEquals(REASONING, ReasoningResolver.resolveMode(persisted = FAST, available = available))
    }

    @Test
    fun whenSomeEffortsHaveAccessAndOthersDoNotThenMissingAreTreatedAsAccessible() {
        // Only MEDIUM has gating data (Pro-only); LOW has none → REASONING accessible (inherits), EXTENDED gated.
        val access = listOf(ReasoningEffortAccess(MEDIUM, listOf("pro"), isAccessible = false))
        val result = ReasoningResolver.availableModes(listOf(LOW, MEDIUM), access)
        val reasoning = result.single { it.mode == REASONING }
        val extended = result.single { it.mode == EXTENDED_REASONING }
        assertEquals(true, reasoning.isAccessible)
        assertNull(reasoning.access)
        assertEquals(false, extended.isAccessible)
        assertEquals(access[0], extended.access)
    }
}
