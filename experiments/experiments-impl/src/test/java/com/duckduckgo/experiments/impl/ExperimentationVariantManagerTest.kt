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
import com.duckduckgo.experiments.api.VariantConfig
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

        testee = VariantManagerImpl(
            mockRandomizer,
            appBuildConfig,
            mockExperimentVariantRepository,
        )
    }

    @Test
    fun whenVariantAlreadyPersistedThenVariantReturned() {
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn("variantKey")

        assertEquals("variantKey", testee.getVariantKey())
    }

    @Test
    fun whenVariantNeverPersistedThenNullReturned() {
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn(null)

        assertEquals(null, testee.getVariantKey())
    }

    @Test
    fun whenGetVariantsKeyThenVariantIsNeverUpdated() {
        testee.getVariantKey()

        verify(mockExperimentVariantRepository, never()).updateVariant(any())
    }

    @Test
    fun whenVariantsConfigUpdatedThenSaveNewVariantsConfig() {
        val variantsConfig = listOf(VariantConfig("variant1", 1.0), VariantConfig("variant2", 1.0))

        testee.saveVariants(variantsConfig)

        verify(mockExperimentVariantRepository).saveVariants(variantsConfig)
    }

    // val variantsConfig = listOf(VariantConfig("variant", 1.0))
    // testee.saveVariants(variantsConfig)
    // Old
    @Test
    fun whenVariantAlreadyPersistedThenVariantAllocatorNeverInvoked() {
        val variantsConfig = listOf(VariantConfig("variantKey", 1.0))
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn("variantKey")
        testee.saveVariants(variantsConfig)

        verify(mockRandomizer, never()).random(any())
    }

    @Test
    fun givenVariantsUpdateWhenVariantNeverPersistedThenVariantAllocatorNeverInvoked() {
        val variantsConfig = listOf(VariantConfig("variantKey", 1.0))
        val testVariantEntity = ExperimentVariantEntity("variantKey", 1.0)
        whenever(mockExperimentVariantRepository.getActiveVariants()).thenReturn(listOf(testVariantEntity))
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn(null)
        testee.saveVariants(variantsConfig)

        verify(mockRandomizer).random(any())
    }

    @Test
    fun whenNoVariantsAvailableThenDefaultVariantIsAssigned() {
        whenever(mockExperimentVariantRepository.getActiveVariants()).thenReturn(emptyList())
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn(null)
        testee.saveVariants(emptyList())

        verify(mockExperimentVariantRepository).updateVariant("")
    }

    @Test
    fun whenVariantPersistedIsNotFoundInActiveVariantListThenRestoredToDefaultVariant() {
        whenever(mockExperimentVariantRepository.getActiveVariants()).thenReturn(emptyList())
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn("variantKey")
        testee.saveVariants(emptyList())

        verify(mockExperimentVariantRepository).updateVariant("")
    }

    @Test
    fun whenVariantPersistedHasWeightEqualToZeroInActiveVariantListThenVariantIsNotRestored() {
        val variantsConfig = listOf(VariantConfig("variantKey", 0.0))
        val testVariantEntity = ExperimentVariantEntity("variantKey", 0.0)
        whenever(mockExperimentVariantRepository.getActiveVariants()).thenReturn(listOf(testVariantEntity))
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn("variantKey")
        testee.saveVariants(variantsConfig)

        verify(mockExperimentVariantRepository, never()).updateVariant(any())
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

        verify(mockExperimentVariantRepository).updateVariant("foo")
    }

    @Test
    fun whenVariantDoesNotComplyWithFiltersThenDefaultVariantIsPersisted() {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        addActiveVariantToConfig(localeFilter = listOf("de_DE"))

        testee.getVariantKey()

        verify(mockExperimentVariantRepository).updateVariant("")
    }

    @Test
    fun whenVariantDoesComplyWithFiltersThenNewVariantKeyIsAllocatedAndPersisted() {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        addActiveVariantToConfig(localeFilter = listOf("en_US"))

        testee.getVariantKey()

        verify(mockExperimentVariantRepository).updateVariant("foo")
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

        verify(mockExperimentVariantRepository).updateAppReferrerVariant("xx")
    }

    @Test
    fun whenUpdatingReferrerVariantThenNewReferrerVariantReturned() {
        val originalVariant = testee.getVariantKey()
        mockUpdateScenario("xx")
        val newVariant = testee.getVariantKey()
        Assert.assertNotEquals(originalVariant, newVariant)
        assertEquals("xx", newVariant)
    }

    private fun addActiveVariantToConfig(variantKey: String = "foo", weight: Double = 1.0, localeFilter: List<String> = emptyList()) {
        val testVariantEntity = ExperimentVariantEntity(variantKey, weight, localeFilter)
        whenever(mockExperimentVariantRepository.getActiveVariants()).thenReturn(listOf(testVariantEntity))

        testee.saveVariants(listOf(VariantConfig(variantKey)))
    }

    private fun mockUpdateScenario(key: String) {
        testee.updateAppReferrerVariant(key)
        whenever(mockExperimentVariantRepository.getAppReferrerVariant()).thenReturn(key)
        whenever(mockExperimentVariantRepository.getUserVariant()).thenReturn(key)
    }
}
