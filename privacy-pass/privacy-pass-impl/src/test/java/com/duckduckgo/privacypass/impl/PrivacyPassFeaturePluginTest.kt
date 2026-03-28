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

package com.duckduckgo.privacypass.impl

import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class PrivacyPassFeaturePluginTest {

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()

    private lateinit var testee: PrivacyPassFeaturePlugin

    @Before
    fun before() {
        testee = PrivacyPassFeaturePlugin(mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchPrivacyPassThenReturnFalse() {
        PrivacyFeatureName.entries
            .filter { it != PrivacyFeatureName.PrivacyPassFeatureName }
            .forEach { privacyFeatureName ->
                assertFalse(testee.store(privacyFeatureName.value, ENABLED_JSON))
            }
    }

    @Test
    fun whenFeatureNameMatchesPrivacyPassThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, ENABLED_JSON))
    }

    @Test
    fun whenFeatureNameMatchesPrivacyPassAndEnabledThenStoreFeatureEnabled() {
        testee.store(FEATURE_NAME, ENABLED_JSON)

        verify(mockFeatureTogglesRepository).insert(
            PrivacyFeatureToggles(FEATURE_NAME, true, null),
        )
    }

    @Test
    fun whenFeatureNameMatchesPrivacyPassAndDisabledThenStoreFeatureDisabled() {
        testee.store(FEATURE_NAME, DISABLED_JSON)

        verify(mockFeatureTogglesRepository).insert(
            PrivacyFeatureToggles(FEATURE_NAME, false, null),
        )
    }

    @Test
    fun whenFeatureNameMatchesPrivacyPassAndMinSupportedVersionPresentThenStoreVersion() {
        testee.store(FEATURE_NAME, MIN_SUPPORTED_JSON)

        verify(mockFeatureTogglesRepository).insert(
            PrivacyFeatureToggles(FEATURE_NAME, true, 52720000),
        )
    }

    @Test
    fun whenJsonMalformedThenReturnFalseAndDoNotStoreFeatureToggle() {
        assertFalse(testee.store(FEATURE_NAME, MALFORMED_JSON))

        verifyNoInteractions(mockFeatureTogglesRepository)
    }

    companion object {
        private const val FEATURE_NAME = "privacyPass"
        private const val ENABLED_JSON = """{"state":"enabled","settings":{},"exceptions":[]}"""
        private const val DISABLED_JSON = """{"state":"disabled","settings":{},"exceptions":[]}"""
        private const val MIN_SUPPORTED_JSON = """{"state":"enabled","minSupportedVersion":52720000,"settings":{},"exceptions":[]}"""
        private const val MALFORMED_JSON = """{"state":"enabled""" // missing closing brace
    }
}
