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

    // ---- requiredTier wrapper smoke tests ----
    // Confirm AIChatModel.requiredTier forwards both fields to lowestRequiredTier.
    // Branch coverage for the resolution logic itself lives in the lowestRequiredTier tests below.

    @Test
    fun requiredTierForwardsAccessTierToHelper() {
        assertEquals(UserTier.PLUS, model(accessTier = listOf("plus"), isAccessible = false).requiredTier)
    }

    @Test
    fun requiredTierForwardsIsAccessibleToHelper() {
        assertEquals(UserTier.FREE, model(accessTier = emptyList(), isAccessible = true).requiredTier)
    }

    // ---- lowestRequiredTier helper ----

    @Test
    fun whenAccessTierIsEmptyAndNotAccessibleThenLowestRequiredTierIsNull() {
        assertNull(lowestRequiredTier(accessTier = emptyList(), isAccessible = false))
    }

    @Test
    fun whenAccessTierIsEmptyAndAccessibleThenLowestRequiredTierIsFree() {
        assertEquals(UserTier.FREE, lowestRequiredTier(accessTier = emptyList(), isAccessible = true))
    }

    @Test
    fun whenAccessTierContainsOnlyFreeThenLowestRequiredTierIsFree() {
        assertEquals(UserTier.FREE, lowestRequiredTier(accessTier = listOf("free"), isAccessible = false))
    }

    @Test
    fun whenAccessTierContainsOnlyPlusThenLowestRequiredTierIsPlus() {
        assertEquals(UserTier.PLUS, lowestRequiredTier(accessTier = listOf("plus"), isAccessible = false))
    }

    @Test
    fun whenAccessTierContainsOnlyProThenLowestRequiredTierIsPro() {
        assertEquals(UserTier.PRO, lowestRequiredTier(accessTier = listOf("pro"), isAccessible = false))
    }

    @Test
    fun whenAccessTierContainsFreeAndPlusThenLowestRequiredTierIsFree() {
        assertEquals(UserTier.FREE, lowestRequiredTier(accessTier = listOf("plus", "free"), isAccessible = false))
    }

    @Test
    fun whenAccessTierContainsPlusAndProThenLowestRequiredTierIsPlus() {
        assertEquals(UserTier.PLUS, lowestRequiredTier(accessTier = listOf("pro", "plus"), isAccessible = false))
    }

    @Test
    fun whenAccessTierContainsFreePlusProThenLowestRequiredTierIsFree() {
        assertEquals(UserTier.FREE, lowestRequiredTier(accessTier = listOf("pro", "plus", "free"), isAccessible = false))
    }

    @Test
    fun whenAccessTierContainsOnlyNonPublicTierThenLowestRequiredTierIsNull() {
        assertNull(lowestRequiredTier(accessTier = listOf("internal"), isAccessible = false))
    }

    @Test
    fun whenAccessTierContainsMultipleNonPublicTiersThenLowestRequiredTierIsNull() {
        assertNull(lowestRequiredTier(accessTier = listOf("internal", "enterprise"), isAccessible = false))
    }

    @Test
    fun whenAccessTierMixesPublicAndNonPublicTiersThenLowestRequiredTierIsTheLowestPublic() {
        assertEquals(UserTier.PLUS, lowestRequiredTier(accessTier = listOf("internal", "plus"), isAccessible = false))
        assertEquals(UserTier.PRO, lowestRequiredTier(accessTier = listOf("internal", "pro"), isAccessible = false))
        assertEquals(UserTier.FREE, lowestRequiredTier(accessTier = listOf("internal", "free"), isAccessible = false))
    }

    private fun model(accessTier: List<String>, isAccessible: Boolean) = AIChatModel(
        id = "id",
        name = "name",
        displayName = "name",
        shortName = "name",
        accessTier = accessTier,
        isAccessible = isAccessible,
    )
}
