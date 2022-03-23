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

package com.duckduckgo.privacy.config.impl.features.amplinks

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.*
import com.duckduckgo.privacy.config.store.features.amplinks.AmpLinksRepository
import junit.framework.TestCase.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AmpLinksPluginTest {

    lateinit var testee: AmpLinksPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockAmpLinksRepository: AmpLinksRepository = mock()

    @Before
    fun before() {
        testee = AmpLinksPlugin(mockAmpLinksRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchAmpLinksThenReturnFalse() {
        PrivacyFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            Assert.assertFalse(testee.store(it, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesAmpLinksThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesAmpLinksAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(AmpLinksPluginTest::class.java.classLoader!!, "json/amp_links.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, true))
    }

    @Test
    fun whenFeatureNameMatchesAmpLinksAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(
            AmpLinksPluginTest::class.java.classLoader!!,
            "json/amp_links_disabled.json"
        )

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, false))
    }

    @Test
    fun whenFeatureNameMatchesAmpLinksThenUpdateAllExistingValues() {
        val jsonString = FileUtilities.loadText(AmpLinksPluginTest::class.java.classLoader!!, "json/amp_links.json")

        testee.store(FEATURE_NAME, jsonString)

        val exceptionArgumentCaptor = argumentCaptor<List<AmpLinkExceptionEntity>>()
        val ampLinkFormatArgumentCaptor = argumentCaptor<List<AmpLinkFormatEntity>>()
        val ampKeywordArgumentCaptor = argumentCaptor<List<AmpKeywordEntity>>()

        verify(mockAmpLinksRepository).updateAll(
            exceptionArgumentCaptor.capture(),
            ampLinkFormatArgumentCaptor.capture(),
            ampKeywordArgumentCaptor.capture(),
        )

        val ampLinkExceptionEntityList = exceptionArgumentCaptor.firstValue

        assertEquals(1, ampLinkExceptionEntityList.size)
        assertEquals("example.com", ampLinkExceptionEntityList.first().domain)
        assertEquals("reason", ampLinkExceptionEntityList.first().reason)

        val ampLinkFormatEntityList = ampLinkFormatArgumentCaptor.firstValue

        assertEquals(1, ampLinkFormatEntityList.size)
        assertEquals("linkFormat", ampLinkFormatEntityList.first().format)

        val ampKeywordEntityList = ampKeywordArgumentCaptor.firstValue

        assertEquals(1, ampKeywordEntityList.size)
        assertEquals("keyword", ampKeywordEntityList.first().keyword)
    }

    companion object {
        private val FEATURE_NAME = PrivacyFeatureName.AmpLinksFeatureName
        private const val EMPTY_JSON_STRING = "{}"
    }
}
