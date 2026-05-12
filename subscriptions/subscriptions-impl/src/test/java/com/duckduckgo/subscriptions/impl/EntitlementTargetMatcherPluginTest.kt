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

package com.duckduckgo.subscriptions.impl

import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EntitlementTargetMatcherPluginTest {

    private val subscriptions: Subscriptions = mock()
    private lateinit var matcher: EntitlementTargetMatcherPlugin

    @Before
    fun setup() {
        matcher = EntitlementTargetMatcherPlugin(subscriptions)
    }

    @Test
    fun whenEntitlementIsNullThenReturnTrue() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(emptyFlow())
        val target = NULL_TARGET.copy(entitlement = null)

        val result = matcher.matchesTargetProperty(target)

        assertTrue(result)
    }

    @Test
    fun whenEntitlementIsNullAndUserHasEntitlementsThenReturnTrue() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR, Product.NetP)))
        val target = NULL_TARGET.copy(entitlement = null)

        val result = matcher.matchesTargetProperty(target)

        assertTrue(result)
    }

    @Test
    fun whenEntitlementMatchesUserEntitlementThenReturnTrue() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
        val target = NULL_TARGET.copy(entitlement = Product.PIR.value)

        val result = matcher.matchesTargetProperty(target)

        assertTrue(result)
    }

    @Test
    fun whenEntitlementMatchesUserEntitlementWithDifferentCaseThenReturnTrue() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
        val target = NULL_TARGET.copy(entitlement = Product.PIR.value.lowercase())

        val result = matcher.matchesTargetProperty(target)

        assertTrue(result)
    }

    @Test
    fun whenEntitlementDoesNotMatchAnyUserEntitlementhenReturnFalse() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP)))
        val target = NULL_TARGET.copy(entitlement = Product.PIR.value)

        val result = matcher.matchesTargetProperty(target)

        assertFalse(result)
    }

    @Test
    fun whenUserHasNoFlowEntitlementAndEntitlementIsSetThenReturnFalse() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(emptyFlow())
        val target = NULL_TARGET.copy(entitlement = Product.PIR.value)

        val result = matcher.matchesTargetProperty(target)

        assertFalse(result)
    }

    @Test
    fun whenUserHasNoProductsAndEntitlementIsSetThenReturnFalse() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        val target = NULL_TARGET.copy(entitlement = Product.PIR.value)

        val result = matcher.matchesTargetProperty(target)

        assertFalse(result)
    }

    @Test
    fun whenUserHasMultipleEntitlementsAndEntitlementMatchesOneThenReturnTrue() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP, Product.ITR, Product.PIR)))
        val target = NULL_TARGET.copy(entitlement = Product.ITR.value)

        val result = matcher.matchesTargetProperty(target)

        assertTrue(result)
    }

    @Test
    fun whenEntitlementIsUnknownValueThenReturnFalse() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR, Product.NetP)))
        val target = NULL_TARGET.copy(entitlement = "Unknown Product")

        val result = matcher.matchesTargetProperty(target)

        assertFalse(result)
    }

    companion object {
        private val NULL_TARGET = Toggle.State.Target(
            variantKey = null,
            localeCountry = null,
            localeLanguage = null,
            isReturningUser = null,
            isPrivacyProEligible = null,
            entitlement = null,
            minSdkVersion = null,
        )
    }
}
