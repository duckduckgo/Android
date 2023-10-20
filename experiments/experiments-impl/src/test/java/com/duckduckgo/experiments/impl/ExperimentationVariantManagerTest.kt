/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.experiments.impl

import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExperimentationVariantManagerTest {

    private lateinit var testee: VariantManagerImpl

    private val mockStore: StatisticsDataStore = mock()
    private val mockRandomizer: IndexRandomizer = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val activeVariants = mutableListOf<Variant>()
    private val mockExperimentVariantRepository: ExperimentVariantRepository = mock()

    @Before
    fun setup() {
        // mock randomizer always returns the first active variant
        whenever(mockRandomizer.random(any())).thenReturn(0)

        testee = VariantManagerImpl(
            mockStore,
            mockRandomizer,
            appBuildConfig,
            mockExperimentVariantRepository,
        )
    }

    @Test
    fun whenVariantAlreadyPersistedThenVariantReturned() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))
        whenever(mockStore.variant).thenReturn("foo")

        assertEquals("foo", testee.getVariantKey())
    }

    @Test
    fun whenVariantAlreadyPersistedThenVariantAllocatorNeverInvoked() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))
        whenever(mockStore.variant).thenReturn("foo")

        testee.getVariantKey()
        verify(mockRandomizer, never()).random(any())
    }

    @Test
    fun whenNoVariantsAvailableThenDefaultVariantHasEmptyStringForKey() {
        whenever(mockStore.variant).thenReturn("foo")

        testee.getVariantKey()
        assertEquals("", testee.defaultVariantKey())
    }

    @Test
    fun whenVariantPersistedIsNotFoundInActiveVariantListThenRestoredToDefaultVariant() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))
        whenever(mockStore.variant).thenReturn("bar")

        assertEquals(testee.defaultVariantKey(), testee.getVariantKey())
    }

    @Test
    fun whenVariantPersistedIsNotFoundInActiveVariantListThenNewVariantIsPersisted() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))

        whenever(mockStore.variant).thenReturn("bar")
        testee.getVariantKey()

        verify(mockStore).variant = testee.defaultVariantKey()
    }

    @Test
    fun whenNoVariantPersistedThenNewVariantAllocated() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))

        testee.getVariantKey()

        verify(mockRandomizer).random(any())
    }

    @Test
    fun whenNoVariantPersistedThenNewVariantKeyIsAllocatedAndPersisted() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))

        testee.getVariantKey()

        verify(mockStore).variant = "foo"
    }

    @Test
    fun whenVariantDoesNotComplyWithFiltersThenDefaultVariantIsPersisted() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { false }))

        testee.getVariantKey()

        verify(mockStore).variant = testee.defaultVariantKey()
    }

    @Test
    fun whenVariantDoesNotComplyWithFiltersUsingAppBuildConfigThenDefaultVariantIsPersisted() {
        whenever(appBuildConfig.sdkInt).thenReturn(10)
        activeVariants.add(Variant("foo", 100.0, filterBy = { config -> config.sdkInt == 11 }))

        testee.getVariantKey()

        verify(mockStore).variant = testee.defaultVariantKey()
    }

    @Test
    fun whenVariantDoesComplyWithFiltersUsingAppBuildConfigThenDefaultVariantIsPersisted() {
        whenever(appBuildConfig.sdkInt).thenReturn(10)
        activeVariants.add(Variant("foo", 100.0, filterBy = { config -> config.sdkInt == 10 }))

        testee.getVariantKey()

        verify(mockStore).variant = "foo"
    }

    @Test
    fun whenVariantDoesComplyWithFiltersThenNewVariantKeyIsAllocatedAndPersisted() {
        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))

        testee.getVariantKey()

        verify(mockStore).variant = "foo"
    }

    @Test
    fun whenReferrerVariantSetWithNoActiveVariantsThenReferrerVariantReturned() {
        val referrerVariantKey = "xx"
        mockUpdateScenario(referrerVariantKey)

        val variantKey = testee.getVariantKey()
        assertEquals(referrerVariantKey, variantKey)
    }

    @Test
    fun whenReferrerVariantSetWithActiveVariantsThenReferrerVariantReturned() {
        val referrerVariantKey = "xx"
        mockUpdateScenario(referrerVariantKey)

        activeVariants.add(Variant("foo", 100.0, filterBy = { true }))
        activeVariants.add(Variant("bar", 100.0, filterBy = { true }))
        val variantKey = testee.getVariantKey()

        assertEquals(referrerVariantKey, variantKey)
    }

    @Test
    fun whenUpdatingReferrerVariantThenDataStoreHasItsDataUpdated() {
        testee.updateAppReferrerVariant("xx")
        verify(mockStore).referrerVariant = "xx"
        verify(mockStore).variant = "xx"
    }

    @Test
    fun whenUpdatingReferrerVariantThenNewReferrerVariantReturned() {
        val originalVariant = testee.getVariantKey()
        mockUpdateScenario("xx")
        val newVariant = testee.getVariantKey()
        Assert.assertNotEquals(originalVariant, newVariant)
        assertEquals("xx", newVariant)
    }

    private fun mockUpdateScenario(key: String) {
        testee.updateAppReferrerVariant(key)
        whenever(mockStore.referrerVariant).thenReturn(key)
        whenever(mockStore.variant).thenReturn(key)
    }
}
