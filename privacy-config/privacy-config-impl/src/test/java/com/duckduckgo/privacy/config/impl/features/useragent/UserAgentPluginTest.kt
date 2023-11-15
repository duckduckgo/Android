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

package com.duckduckgo.privacy.config.impl.features.useragent

import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.UserAgentExceptionEntity
import com.duckduckgo.privacy.config.store.UserAgentSitesEntity
import com.duckduckgo.privacy.config.store.UserAgentStatesEntity
import com.duckduckgo.privacy.config.store.UserAgentVersionsEntity
import com.duckduckgo.privacy.config.store.features.useragent.UserAgentRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class UserAgentPluginTest {
    lateinit var testee: UserAgentPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockUserAgentRepository: UserAgentRepository = mock()

    @Before
    fun before() {
        testee = UserAgentPlugin(mockUserAgentRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchUserAgentThenReturnFalse() {
        PrivacyFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesUserAgentThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME_VALUE, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesUserAgentAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/useragent.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME_VALUE, true, null))
    }

    @Test
    fun whenFeatureNameMatchesUserAgentAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/useragent_disabled.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME_VALUE, false, null))
    }

    @Test
    fun whenFeatureNameMatchesUserAgentAndHasMinSupportedVersionThenStoreMinSupportedVersion() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/useragent_min_supported_version.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME_VALUE, true, 1234))
    }

    @Test
    fun whenFeatureNameMatchesUserAgentThenUpdateAllExistingExceptions() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/useragent.json")
        val exceptionsCaptor = argumentCaptor<List<UserAgentExceptionEntity>>()
        val sitesCaptor = argumentCaptor<List<UserAgentSitesEntity>>()
        val statesCaptor = argumentCaptor<UserAgentStatesEntity>()
        val versionsCaptor = argumentCaptor<List<UserAgentVersionsEntity>>()

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockUserAgentRepository).updateAll(
            exceptionsCaptor.capture(),
            sitesCaptor.capture(),
            statesCaptor.capture(),
            versionsCaptor.capture(),
        )

        val exceptions = exceptionsCaptor.firstValue
        val sites = sitesCaptor.firstValue
        val states = statesCaptor.firstValue
        val versions = versionsCaptor.firstValue

        val default = exceptions.first { it.domain == "default.com" }
        val application = exceptions.first { it.domain == "application.com" }
        val version = exceptions.first { it.domain == "version.com" }
        val duplicatedApplication = exceptions.first { it.domain == "duplicatedapplication.com" }
        val duplicatedVersion = exceptions.first { it.domain == "duplicatedversion.com" }
        val versionApplication = exceptions.first { it.domain == "versionapplication.com" }

        assertFalse(default.omitApplication)
        assertFalse(default.omitVersion)

        assertTrue(application.omitApplication)
        assertFalse(application.omitVersion)

        assertTrue(version.omitVersion)
        assertFalse(version.omitApplication)

        assertFalse(duplicatedApplication.omitApplication)
        assertFalse(duplicatedApplication.omitVersion)

        assertFalse(duplicatedVersion.omitApplication)
        assertFalse(duplicatedVersion.omitVersion)

        assertTrue(versionApplication.omitApplication)
        assertTrue(versionApplication.omitVersion)

        val defaultSite = sites.first { it.domain == "defaultsite.com" }
        val fixedSite = sites.first { it.domain == "fixedsite.com" }

        assertTrue(defaultSite.ddgDefaultSite)
        assertTrue(fixedSite.ddgFixedSite)

        assertTrue(states.closestUserAgent)
        assertTrue(states.ddgFixedUserAgent)

        assertEquals(versions.filter { it.closestUserAgent }.map { it.version }, listOf("123", "456"))
        assertEquals(versions.filter { it.ddgFixedUserAgent }.map { it.version }, listOf("789", "321"))
    }

    companion object {
        private val FEATURE_NAME = PrivacyFeatureName.UserAgentFeatureName
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val EMPTY_JSON_STRING = "{}"
    }
}
