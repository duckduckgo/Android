/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.cookies.impl.features

import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.cookies.api.CookiesFeatureName
import com.duckduckgo.cookies.store.CookieEntity
import com.duckduckgo.cookies.store.CookieExceptionEntity
import com.duckduckgo.cookies.store.CookieNamesEntity
import com.duckduckgo.cookies.store.CookiesFeatureToggleRepository
import com.duckduckgo.cookies.store.CookiesFeatureToggles
import com.duckduckgo.cookies.store.CookiesRepository
import com.duckduckgo.cookies.store.FirstPartyCookiePolicyEntity
import com.duckduckgo.cookies.store.contentscopescripts.ContentScopeScriptsCookieRepository
import junit.framework.TestCase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class CookiesFeaturePluginTest {
    lateinit var testee: CookiesFeaturePlugin

    private val mockFeatureTogglesRepository: CookiesFeatureToggleRepository = mock()
    private val mockCookiesRepository: CookiesRepository = mock()
    private val mockContentScopeCookieRepository: ContentScopeScriptsCookieRepository = mock()

    @Before
    fun before() {
        testee = CookiesFeaturePlugin(mockCookiesRepository, mockContentScopeCookieRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchCookiesThenReturnFalse() {
        CookiesFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesCookiesThenReturnTrue() {
        TestCase.assertTrue(testee.store(FEATURE_NAME_VALUE, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesCookiesAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(CookiesFeaturePluginTest::class.java.classLoader!!, "json/cookies.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(CookiesFeatureToggles(FEATURE_NAME, true, null))
    }

    @Test
    fun whenFeatureNameMatchesCookiesAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(
            CookiesFeaturePluginTest::class.java.classLoader!!,
            "json/cookies_disabled.json",
        )

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(CookiesFeatureToggles(FEATURE_NAME, false, null))
    }

    @Test
    fun whenFeatureNameMatchesCookiesAndHasMinSupportedVersionThenStoreMinSupportedVersion() {
        val jsonString = FileUtilities.loadText(CookiesFeaturePluginTest::class.java.classLoader!!, "json/cookies_min_supported_version.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(CookiesFeatureToggles(FEATURE_NAME, true, 1234))
    }

    @Test
    fun whenFeatureNameMatchesCookiesThenUpdateAllExistingValues() {
        val jsonString = FileUtilities.loadText(CookiesFeaturePluginTest::class.java.classLoader!!, "json/cookies.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        val exceptionArgumentCaptor = argumentCaptor<List<CookieExceptionEntity>>()
        val policyArgumentCaptor = argumentCaptor<FirstPartyCookiePolicyEntity>()
        val cookieNameCaptor = argumentCaptor<List<CookieNamesEntity>>()

        verify(mockCookiesRepository).updateAll(
            exceptionArgumentCaptor.capture(),
            policyArgumentCaptor.capture(),
            cookieNameCaptor.capture(),
        )

        val cookieExceptionEntityList = exceptionArgumentCaptor.firstValue

        assertEquals(1, cookieExceptionEntityList.size)
        assertEquals("example.com", cookieExceptionEntityList.first().domain)
        assertEquals("reason", cookieExceptionEntityList.first().reason)

        val cookiePolicyEntity = policyArgumentCaptor.firstValue

        assertEquals(1, cookiePolicyEntity.threshold)
        assertEquals(2, cookiePolicyEntity.maxAge)

        val cookieCaptor = argumentCaptor<CookieEntity>()

        verify(mockContentScopeCookieRepository).updateAll(
            cookieCaptor.capture(),
        )

        val cookieEntity = cookieCaptor.firstValue

        assertEquals(jsonString, cookieEntity.json)

        val cookieNamesEntityList = cookieNameCaptor.firstValue

        assertEquals(2, cookieNamesEntityList.size)
        assertEquals("cookie1", cookieNamesEntityList.first().name)
        assertEquals("cookie2", cookieNamesEntityList.last().name)
    }

    companion object {
        private val FEATURE_NAME = CookiesFeatureName.Cookie
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val EMPTY_JSON_STRING = "{}"
    }
}
