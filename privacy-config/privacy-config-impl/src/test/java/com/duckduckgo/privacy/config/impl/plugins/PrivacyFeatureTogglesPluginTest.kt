/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.plugins

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FeatureName
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class PrivacyFeatureTogglesPluginTest {

    @get:Rule var coroutineRule = CoroutineTestRule()
    lateinit var testee: PrivacyFeatureTogglesPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()

    @Before
    fun before() {
        testee = PrivacyFeatureTogglesPlugin(mockFeatureTogglesRepository)
    }

    @Test
    fun whenIsEnabledAndFeatureIsNotAPrivacyFeatureThenReturnNull() = runTest {
        assertNull(testee.isEnabled(NonPrivacyFeature(), true))
    }

    @Test
    fun whenIsEnabledAndFeatureIsPrivacyFeatureThenReturnTrueWhenEnabled() =
        runTest {
            givenPrivacyFeatureIsEnabled()

            val isEnabled = testee.isEnabled(PrivacyFeatureName.ContentBlockingFeatureName, true)

            assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsPrivacyFeatureThenReturnFalseWhenDisabled() =
        runTest {
            givenPrivacyFeatureIsDisabled()

            val isEnabled = testee.isEnabled(PrivacyFeatureName.ContentBlockingFeatureName, true)

            assertFalse(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsPrivacyFeatureThenReturnDefaultValueIfFeatureDoesNotExist() =
        runTest {
            val defaultValue = true
            givenPrivacyFeatureReturnsDefaultValue(defaultValue)

            val isEnabled =
                testee.isEnabled(PrivacyFeatureName.ContentBlockingFeatureName, defaultValue)

            assertEquals(defaultValue, isEnabled)
        }

    private fun givenPrivacyFeatureIsEnabled() {
        whenever(
            mockFeatureTogglesRepository.get(
                PrivacyFeatureName.ContentBlockingFeatureName, true
            )
        )
            .thenReturn(true)
    }

    private fun givenPrivacyFeatureIsDisabled() {
        whenever(
            mockFeatureTogglesRepository.get(
                PrivacyFeatureName.ContentBlockingFeatureName, true
            )
        )
            .thenReturn(false)
    }

    private fun givenPrivacyFeatureReturnsDefaultValue(defaultValue: Boolean) {
        whenever(
            mockFeatureTogglesRepository.get(
                PrivacyFeatureName.ContentBlockingFeatureName, defaultValue
            )
        )
            .thenReturn(defaultValue)
    }

    data class NonPrivacyFeature(override val value: String = "test") : FeatureName
}
