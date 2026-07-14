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

package com.duckduckgo.app.fire.promo

import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FireTabsPromoAttributeMatcherTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val fireModeAvailability: FireModeAvailability = mock()
    private val fireDataStore: FireDataStore = mock()

    private val testee = FireTabsPromoAttributeMatcher(
        fireModeAvailability = fireModeAvailability,
        fireDataStore = fireDataStore,
    )

    // --- map ---

    @Test
    fun whenMapFireModeAvailableTrueThenReturnsFireModeAvailableMatchingAttribute() {
        val result = testee.map(FireTabsPromoAttributeMatcher.ATTRIBUTE_FIRE_MODE_AVAILABLE, JsonMatchingAttribute(value = true))
        assertEquals(FireModeAvailableMatchingAttribute(true), result)
    }

    @Test
    fun whenMapFireModeAvailableFalseThenReturnsFireModeAvailableMatchingAttribute() {
        val result = testee.map(FireTabsPromoAttributeMatcher.ATTRIBUTE_FIRE_MODE_AVAILABLE, JsonMatchingAttribute(value = false))
        assertEquals(FireModeAvailableMatchingAttribute(false), result)
    }

    @Test
    fun whenMapUsedFireModeTrueThenReturnsUsedFireModeMatchingAttribute() {
        val result = testee.map(FireTabsPromoAttributeMatcher.ATTRIBUTE_USED_FIRE_MODE, JsonMatchingAttribute(value = true))
        assertEquals(UsedFireModeMatchingAttribute(true), result)
    }

    @Test
    fun whenMapUsedFireModeFalseThenReturnsUsedFireModeMatchingAttribute() {
        val result = testee.map(FireTabsPromoAttributeMatcher.ATTRIBUTE_USED_FIRE_MODE, JsonMatchingAttribute(value = false))
        assertEquals(UsedFireModeMatchingAttribute(false), result)
    }

    @Test
    fun whenMapUnknownKeyThenReturnsNull() {
        val result = testee.map("somethingElse", JsonMatchingAttribute(value = true))
        assertNull(result)
    }

    @Test
    fun whenMapFireModeAvailableWithNonBooleanValueThenReturnsNull() {
        val result = testee.map(FireTabsPromoAttributeMatcher.ATTRIBUTE_FIRE_MODE_AVAILABLE, JsonMatchingAttribute(value = "not-a-boolean"))
        assertNull(result)
    }

    // --- evaluate: FireModeAvailable ---

    @Test
    fun whenEvaluateFireModeAvailableAndStateMatchesThenTrue() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(true)
        assertTrue(testee.evaluate(FireModeAvailableMatchingAttribute(true))!!)
    }

    @Test
    fun whenEvaluateFireModeAvailableAndStateDoesNotMatchThenFalse() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)
        assertFalse(testee.evaluate(FireModeAvailableMatchingAttribute(true))!!)
    }

    @Test
    fun whenEvaluateFireModeUnavailableAndStateMatchesThenTrue() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)
        assertTrue(testee.evaluate(FireModeAvailableMatchingAttribute(false))!!)
    }

    // --- evaluate: UsedFireMode ---

    @Test
    fun whenEvaluateUsedFireModeAndStateMatchesThenTrue() = runTest {
        whenever(fireDataStore.hasUsedFireMode()).thenReturn(true)
        assertTrue(testee.evaluate(UsedFireModeMatchingAttribute(true))!!)
    }

    @Test
    fun whenEvaluateUsedFireModeAndStateDoesNotMatchThenFalse() = runTest {
        whenever(fireDataStore.hasUsedFireMode()).thenReturn(false)
        assertFalse(testee.evaluate(UsedFireModeMatchingAttribute(true))!!)
    }

    @Test
    fun whenEvaluateNotUsedFireModeAndStateMatchesThenTrue() = runTest {
        whenever(fireDataStore.hasUsedFireMode()).thenReturn(false)
        assertTrue(testee.evaluate(UsedFireModeMatchingAttribute(false))!!)
    }

    // --- evaluate: unrelated attribute ---

    @Test
    fun whenEvaluateUnrelatedMatchingAttributeThenNull() = runTest {
        val unrelated = object : MatchingAttribute {}
        assertNull(testee.evaluate(unrelated))
    }
}
