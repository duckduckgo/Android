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

package com.duckduckgo.privacy.config.impl.features.gpc

import com.duckduckgo.privacy.config.api.GpcException
import com.duckduckgo.privacy.config.api.GpcHeaderEnabledSite
import com.duckduckgo.privacy.config.api.PrivacyFeatureName.GpcFeatureName
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList

class GpcContentScopeConfigPluginTest {

    lateinit var testee: GpcContentScopeConfigPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockGpcRepository: GpcRepository = mock()

    @Before
    fun before() {
        testee = GpcContentScopeConfigPlugin(mockGpcRepository, mockFeatureTogglesRepository)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(true)
        whenever(mockFeatureTogglesRepository.getMinSupportedVersion(GpcFeatureName)).thenReturn(123)
        val gpcExceptions = listOf(GpcException("example.com"))
        whenever(mockGpcRepository.exceptions).thenReturn(CopyOnWriteArrayList(gpcExceptions))
        val headerEnabledSites = listOf(GpcHeaderEnabledSite("foo.com"))
        whenever(mockGpcRepository.headerEnabledSites).thenReturn(CopyOnWriteArrayList(headerEnabledSites))
    }

    @Test
    fun whenGetConfigThenReturnCorrectlyFormattedJsonWhenGpcIsEnabled() {
        val config = testee.config()
        assertEquals("\"gpc\":{" +
            "\"exceptions\":[{\"domain\":\"example.com\"}]," +
            "\"minSupportedVersion\":123," +
            "\"settings\":{\"gpcHeaderEnabledSites\":[\"foo.com\"]}," +
            "\"state\":\"enabled\"}",
            config)
    }

    @Test
    fun whenGetConfigThenReturnCorrectlyFormattedJsonWhenGpcIsDisabled() {
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(false)

        val config = testee.config()
        assertEquals("\"gpc\":{" +
            "\"exceptions\":[{\"domain\":\"example.com\"}]," +
            "\"minSupportedVersion\":123," +
            "\"settings\":{\"gpcHeaderEnabledSites\":[\"foo.com\"]}," +
            "\"state\":\"disabled\"}",
            config)
    }
}
