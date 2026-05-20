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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AIChatModelTest {

    @Test
    fun whenAccessTierIsEmptyThenRequiredTierIsFree() {
        assertEquals(UserTier.FREE, model(accessTier = emptyList()).requiredTier)
    }

    @Test
    fun whenAccessTierContainsOnlyFreeThenRequiredTierIsFree() {
        assertEquals(UserTier.FREE, model(accessTier = listOf("free")).requiredTier)
    }

    @Test
    fun whenAccessTierContainsOnlyPlusThenRequiredTierIsPlus() {
        assertEquals(UserTier.PLUS, model(accessTier = listOf("plus")).requiredTier)
    }

    @Test
    fun whenAccessTierContainsOnlyProThenRequiredTierIsPro() {
        assertEquals(UserTier.PRO, model(accessTier = listOf("pro")).requiredTier)
    }

    @Test
    fun whenAccessTierContainsFreeAndPlusThenRequiredTierIsFree() {
        // FREE has priority over PLUS in the lookup.
        assertEquals(UserTier.FREE, model(accessTier = listOf("plus", "free")).requiredTier)
    }

    @Test
    fun whenAccessTierContainsPlusAndProThenRequiredTierIsPlus() {
        // PLUS has priority over PRO in the lookup.
        assertEquals(UserTier.PLUS, model(accessTier = listOf("pro", "plus")).requiredTier)
    }

    @Test
    fun whenAccessTierContainsFreePlusProThenRequiredTierIsFree() {
        assertEquals(UserTier.FREE, model(accessTier = listOf("pro", "plus", "free")).requiredTier)
    }

    @Test
    fun whenAccessTierContainsOnlyNonPublicTierThenRequiredTierIsNull() {
        assertNull(model(accessTier = listOf("internal")).requiredTier)
    }

    @Test
    fun whenAccessTierContainsMultipleNonPublicTiersThenRequiredTierIsNull() {
        assertNull(model(accessTier = listOf("internal", "enterprise")).requiredTier)
    }

    @Test
    fun whenAccessTierMixesPublicAndNonPublicTiersThenRequiredTierIsTheLowestPublic() {
        assertEquals(UserTier.PLUS, model(accessTier = listOf("internal", "plus")).requiredTier)
        assertEquals(UserTier.PRO, model(accessTier = listOf("internal", "pro")).requiredTier)
        assertEquals(UserTier.FREE, model(accessTier = listOf("internal", "free")).requiredTier)
    }

    private fun model(accessTier: List<String>) = AIChatModel(
        id = "id",
        name = "name",
        displayName = "name",
        shortName = "name",
        accessTier = accessTier,
        isAccessible = false,
    )
}
