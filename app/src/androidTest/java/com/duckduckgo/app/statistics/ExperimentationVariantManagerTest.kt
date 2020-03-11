/*
 * Copyright (c) 2018 DuckDuckGo
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

@file:Suppress("SameParameterValue")

package com.duckduckgo.app.statistics

import com.duckduckgo.app.statistics.VariantManager.Companion.RESERVED_EU_AUCTION_VARIANT
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExperimentationVariantManagerTest {

    private lateinit var testee: ExperimentationVariantManager

    private val mockStore: StatisticsDataStore = mock()
    private val mockRandomizer: IndexRandomizer = mock()
    private val activeVariants = mutableListOf<Variant>()

    @Before
    fun setup() {
        // mock randomizer always returns the first active variant
        whenever(mockRandomizer.random(any())).thenReturn(0)

        testee = ExperimentationVariantManager(mockStore, mockRandomizer)
    }

    @Test
    fun whenVariantAlreadyPersistedThenVariantReturned() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))
        whenever(mockStore.variant).thenReturn("foo")

        assertEquals("foo", testee.getVariant(activeVariants).key)
    }

    @Test
    fun whenVariantAlreadyPersistedThenVariantAllocatorNeverInvoked() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))
        whenever(mockStore.variant).thenReturn("foo")

        testee.getVariant(activeVariants)
        verify(mockRandomizer, never()).random(any())
    }

    @Test
    fun whenNoVariantsAvailableThenDefaultVariantHasEmptyStringForKey() {
        whenever(mockStore.variant).thenReturn("foo")

        val defaultVariant = testee.getVariant(activeVariants)
        assertEquals("", defaultVariant.key)
        assertTrue(defaultVariant.features.isEmpty())
    }

    @Test
    fun whenNoVariantsAvailableThenDefaultVariantHasNoExperimentalFeaturesEnabled() {
        whenever(mockStore.variant).thenReturn("foo")

        val defaultVariant = testee.getVariant(activeVariants)
        assertTrue(defaultVariant.features.isEmpty())
    }

    @Test
    fun whenVariantPersistedIsNotFoundInActiveVariantListThenRestoredToDefaultVariant() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))
        whenever(mockStore.variant).thenReturn("bar")

        assertEquals(VariantManager.DEFAULT_VARIANT, testee.getVariant(activeVariants))
    }

    @Test
    fun whenVariantPersistedIsNotFoundInActiveVariantListThenNewVariantIsPersisted() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))

        whenever(mockStore.variant).thenReturn("bar")
        testee.getVariant(activeVariants)

        verify(mockStore).variant = VariantManager.DEFAULT_VARIANT.key
    }

    @Test
    fun whenNoVariantPersistedThenNewVariantAllocated() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))

        testee.getVariant(activeVariants)

        verify(mockRandomizer).random(any())
    }

    @Test
    fun whenNoVariantPersistedThenNewVariantKeyIsAllocatedAndPersisted() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))

        testee.getVariant(activeVariants)

        verify(mockStore).variant = "foo"
    }

    @Test
    fun whenVariantDoesNotComplyWithFiltersThenDefaultVariantIsPersisted() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { false }))

        testee.getVariant(activeVariants)

        verify(mockStore).variant = VariantManager.DEFAULT_VARIANT.key
    }

    @Test
    fun whenVariantDoesComplyWithFiltersThenNewVariantKeyIsAllocatedAndPersisted() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))

        testee.getVariant(activeVariants)

        verify(mockStore).variant = "foo"
    }

    @Test
    fun whenReferrerVariantSetWithNoActiveVariantsThenReferrerVariantReturned() {
        val referrerVariantKey = "xx"
        mockUpdateScenario(referrerVariantKey)

        val variant = testee.getVariant(emptyList())
        assertEquals(referrerVariantKey, variant.key)
    }

    @Test
    fun whenReferrerVariantSetWithActiveVariantsThenReferrerVariantReturned() {
        val referrerVariantKey = "xx"
        mockUpdateScenario(referrerVariantKey)

        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))
        activeVariants.add(Variant("bar", 100.0, filterBy = { true }))
        val variant = testee.getVariant(activeVariants)

        assertEquals(referrerVariantKey, variant.key)
    }

    @Test
    fun whenUpdatingReferrerVariantThenDataStoreHasItsDataUpdated() {
        testee.updateAppReferrerVariant("xx")
        verify(mockStore).referrerVariant = "xx"
        verify(mockStore).variant = "xx"
    }

    @Test
    fun whenUpdatingReferrerVariantThenNewReferrerVariantReturned() {
        val originalVariant = testee.getVariant(activeVariants)
        mockUpdateScenario("xx")
        val newVariant = testee.getVariant(activeVariants)
        assertNotEquals(originalVariant, newVariant)
        assertEquals("xx", newVariant.key)
    }

    @Test
    fun whenUnknownReferrerVariantReturnedThenNoFeaturesEnabled() {
        mockUpdateScenario("xx")
        val variant = testee.getVariant(activeVariants)
        assertTrue(variant.features.isEmpty())
    }

    @Test
    fun whenEuAuctionReferrerVariantReturnedThenSuppressWidgetFeaturesEnabled() {
        mockUpdateScenario(RESERVED_EU_AUCTION_VARIANT)
        val variant = testee.getVariant(activeVariants)
        assertTrue(variant.hasFeature(VariantManager.VariantFeature.SuppressHomeTabWidgetCta))
    }

    private fun mockUpdateScenario(key: String) {
        testee.updateAppReferrerVariant(key)
        whenever(mockStore.referrerVariant).thenReturn(key)
        whenever(mockStore.variant).thenReturn(key)
    }
}