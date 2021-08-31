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
import org.json.JSONObject
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
        assertFalse(testee.store("test", null))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, null))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingAndIsEnabledThenStoreFeatureEnabled() {
        val jsonObject = getJsonObjectFromFile("json/content_blocking.json")

        testee.store(FEATURE_NAME, jsonObject)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, true))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonObject = getJsonObjectFromFile("json/content_blocking_disabled.json")

        testee.store(FEATURE_NAME, jsonObject)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, false))
    }

    @Test
    fun whenFeatureNameMatchesContentBlockingThenUpdateAllExistingExceptions() {
        val jsonObject = getJsonObjectFromFile("json/content_blocking.json")

        testee.store(FEATURE_NAME, jsonObject)

        verify(mockContentBlockingRepository).updateAll(anyList())
    }

    private fun getJsonObjectFromFile(filename: String): JSONObject {
        val json = FileUtilities.loadText(filename)
        return JSONObject(json)
    }

    companion object {
        private const val FEATURE_NAME = "contentBlocking"
    }
}
