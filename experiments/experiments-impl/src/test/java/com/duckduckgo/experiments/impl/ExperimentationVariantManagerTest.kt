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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.experiments.impl.store.ExperimentVariantEntity
import java.util.Locale
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

    private val mockRandomizer: IndexRandomizer = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val activeVariants = mutableListOf<Variant>()
    private val mockExperimentVariantRepository: ExperimentVariantRepository = mock()

    @Before
    fun setup() {
        // mock randomizer always returns the first active variant
        whenever(mockRandomizer.random(any())).thenReturn(0)
        whenever(mockExperimentVariantRepository.getActiveVariants()).thenReturn(emptyList())

        testee = VariantManagerImpl(
            mockRandomizer,
            appBuildConfig,
            mockExperimentVariantRepository,
        )
    }

    @Test
    fun whenVariantAlreadyPersistedThenVariantReturned() {
        addActiveVariantToConfig()
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn("foo")

        assertEquals("foo", testee.getVariantKey())
    }

    @Test
    fun whenVariantAlreadyPersistedThenVariantAllocatorNeverInvoked() {
        addActiveVariantToConfig()
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn("foo")

        testee.getVariantKey()
        verify(mockRandomizer, never()).random(any())
    }

    @Test
    fun whenNoVariantsAvailableThenDefaultVariantHasEmptyStringForKey() {
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn("foo")

        testee.getVariantKey()
        assertEquals("", testee.defaultVariantKey())
    }

    @Test
    fun whenVariantPersistedIsNotFoundInActiveVariantListThenRestoredToDefaultVariant() {
        addActiveVariantToConfig()
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn("bar")

        assertEquals(testee.defaultVariantKey(), testee.getVariantKey())
    }

    @Test
    fun whenVariantPersistedIsNotFoundInActiveVariantListThenNewVariantIsPersisted() {
        addActiveVariantToConfig()
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn("bar")

        testee.getVariantKey()
        verify(mockStore).variant = testee.defaultVariantKey()
    }

    @Test
    fun whenNoVariantPersistedThenNewVariantAllocated() {
        addActiveVariantToConfig()

        testee.getVariantKey()
        verify(mockRandomizer).random(any())
    }

    @Test
    fun whenNoVariantPersistedThenNewVariantKeyIsAllocatedAndPersisted() {
        addActiveVariantToConfig()

        testee.getVariantKey()

        verify(mockStore).variant = "foo"
    }

    @Test
    fun whenVariantDoesNotComplyWithFiltersThenDefaultVariantIsPersisted() {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        addActiveVariantToConfig(localeFilter = listOf("de_DE"))

        testee.getVariantKey()

        verify(mockStore).variant = testee.defaultVariantKey()
    }

    @Test
    fun whenVariantDoesComplyWithFiltersThenNewVariantKeyIsAllocatedAndPersisted() {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        addActiveVariantToConfig(localeFilter = listOf("en_US"))

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

    private fun addActiveVariantToConfig(variantKey: String = "foo", localeFilter: List<String> = emptyList()) {
        val testVariantEntity = ExperimentVariantEntity(variantKey, 1.0, localeFilter)
        whenever(mockExperimentVariantRepository.getActiveVariants()).thenReturn(listOf(testVariantEntity))
    }

    private fun mockUpdateScenario(key: String) {
        testee.updateAppReferrerVariant(key)
        whenever(mockStore.referrerVariant).thenReturn(key)
        whenever(mockStore.variant).thenReturn(key)
    }
}
