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

package com.duckduckgo.privacy.config.impl.features.contentblocking

import com.duckduckgo.privacy.config.impl.FileUtilities
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingRepository
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList

class ContentBlockingPluginTest {
    lateinit var testee: ContentBlockingPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockContentBlockingRepository: ContentBlockingRepository = mock()

    @Before
    fun before() {
        testee = ContentBlockingPlugin(mockContentBlockingRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchContentBlockingThenReturnFalse() {
        assertFalse(testee.store("test", EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText("json/content_blocking.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, true))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText("json/content_blocking_disabled.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, false))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingThenUpdateAllExistingExceptions() {
        val jsonString = FileUtilities.loadText("json/content_blocking.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockContentBlockingRepository).updateAll(anyList())
    }

    companion object {
        private const val FEATURE_NAME = "contentBlocking"
        private const val EMPTY_JSON_STRING = "{}"
    }
}
