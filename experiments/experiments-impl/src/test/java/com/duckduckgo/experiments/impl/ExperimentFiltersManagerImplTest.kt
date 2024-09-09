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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.api.VariantFilters
import com.duckduckgo.subscriptions.api.Subscriptions
import java.util.Locale
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExperimentFiltersManagerImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: ExperimentFiltersManager

    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockSubscriptions: Subscriptions = mock()

    @Before
    fun setup() {
        testee = ExperimentFiltersManagerImpl(
            mockAppBuildConfig,
            mockSubscriptions,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenVariantComplyWithLocaleFilterThenAddFiltersReturnsTrue() {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        val testEntity = addActiveVariant(localeFilter = listOf("en_US"))

        assertTrue(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    @Test
    fun whenVariantDoesNotComplyWithLocaleFilterThenAddFiltersReturnsFalse() {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        val testEntity = addActiveVariant(localeFilter = listOf("de_DE"))

        assertFalse(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    @Test
    fun whenVariantComplyWithAndroidVersionFilterThenAddFiltersReturnsTrue() {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(33)
        val testEntity = addActiveVariant(androidVersionFilter = listOf("33", "34"))

        assertTrue(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    @Test
    fun whenVariantDoesNotComplyWithAndroidVersionFilterThenAddFiltersReturnsFalse() {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(32)
        val testEntity = addActiveVariant(androidVersionFilter = listOf("33", "34"))

        assertFalse(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    @Test
    fun whenVariantComplyWithPrivacyProEligibleFilterThenAddFiltersReturnsTrue() = runTest {
        whenever(mockSubscriptions.isEligible()).thenReturn(true)
        val testEntity = addActiveVariant(privacyProEligible = true)

        assertTrue(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    @Test
    fun whenVariantDoesNotComplyWithPrivacyProEligibleFilterThenAddFiltersReturnsFalse() = runTest {
        whenever(mockSubscriptions.isEligible()).thenReturn(false)
        val testEntity = addActiveVariant(privacyProEligible = true)

        assertFalse(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    @Test
    fun whenVariantComplyWithAllFiltersThenAddFiltersReturnsTrue() = runTest {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        whenever(mockAppBuildConfig.sdkInt).thenReturn(33)
        whenever(mockSubscriptions.isEligible()).thenReturn(false)
        val testEntity = addActiveVariant(
            localeFilter = listOf("en_US"),
            androidVersionFilter = listOf("33", "34"),
            privacyProEligible = false,
        )

        assertTrue(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    @Test
    fun whenVariantComplyWithLocaleFiltersAndDoesNotComplyWithAndroidVersionFilterThenAddFiltersReturnsFalse() {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        whenever(mockAppBuildConfig.sdkInt).thenReturn(32)
        val testEntity = addActiveVariant(localeFilter = listOf("en_US"), androidVersionFilter = listOf("33", "34"))

        assertFalse(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    @Test
    fun whenVariantComplyWithAndroidVersionFiltersAndDoesNotComplyWithLocaleFilterThenAddFiltersReturnsFalse() {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        whenever(mockAppBuildConfig.sdkInt).thenReturn(33)
        val testEntity = addActiveVariant(localeFilter = listOf("de_DE"), androidVersionFilter = listOf("33", "34"))

        assertFalse(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    @Test
    fun whenVariantComplyWithLocaleAndAndroidVersionFiltersAndDoesNotComplyWithPrivacyProEligibleThenAddFiltersReturnsFalse() = runTest {
        val locale = Locale("en", "US")
        Locale.setDefault(locale)
        whenever(mockAppBuildConfig.sdkInt).thenReturn(33)
        whenever(mockSubscriptions.isEligible()).thenReturn(true)
        val testEntity = addActiveVariant(
            localeFilter = listOf("en_US"),
            androidVersionFilter = listOf("33", "34"),
            privacyProEligible = false,
        )

        assertFalse(testee.addFilters(testEntity).invoke(mockAppBuildConfig))
    }

    private fun addActiveVariant(
        localeFilter: List<String> = listOf(),
        androidVersionFilter: List<String> = listOf(),
        privacyProEligible: Boolean? = null,
    ): VariantConfig {
        return VariantConfig("key", 1.0, VariantFilters(localeFilter, androidVersionFilter, privacyProEligible))
    }
}
