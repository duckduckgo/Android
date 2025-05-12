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

package com.duckduckgo.privacy.config.impl.features.trackingparameters

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealTrackingParametersTest {

    private lateinit var testee: RealTrackingParameters
    private val mockTrackingParametersRepository: TrackingParametersRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()

    @Before
    fun setup() {
        givenFeatureEnabled(true)
        whenever(mockTrackingParametersRepository.exceptions).thenReturn(emptyList())
        whenever(mockTrackingParametersRepository.parameters).thenReturn(listOf(TRACKING_PARAMETER))
        whenever(mockUserAllowListRepository.isUrlInUserAllowList(anyString())).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(anyString())).thenReturn(false)
        testee = RealTrackingParameters(mockTrackingParametersRepository, mockFeatureToggle, mockUnprotectedTemporary, mockUserAllowListRepository)
    }

    @Test
    fun whenCleanTrackingParametersAndFeatureIsDisabledThenReturnNull() {
        givenFeatureEnabled(false)
        assertNull(testee.cleanTrackingParameters(null, URL))
    }

    @Test
    fun whenCleanTrackingParametersAndUrlIsInAllowListThenReturnNull() {
        whenever(mockUserAllowListRepository.isUrlInUserAllowList(anyString())).thenReturn(true)
        assertNull(testee.cleanTrackingParameters(null, URL))
    }

    @Test
    fun whenCleanTrackingParametersAndUrlIsUnprotectedTemporaryExceptionThenReturnNull() {
        whenever(mockUnprotectedTemporary.isAnException(anyString())).thenReturn(true)
        assertNull(testee.cleanTrackingParameters(null, URL))
    }

    @Test
    fun whenCleanTrackingParametersAndUrlDomainIsExceptionThenReturnNull() {
        whenever(mockTrackingParametersRepository.exceptions).thenReturn(listOf(FeatureException(domain = "example.com", reason = "reason")))
        assertNull(testee.cleanTrackingParameters(null, URL))
    }

    @Test
    fun whenCleanTrackingParametersAndUrlSubdomainIsExceptionThenReturnNull() {
        whenever(mockTrackingParametersRepository.exceptions).thenReturn(listOf(FeatureException(domain = "example.com", reason = "reason")))
        assertNull(testee.cleanTrackingParameters(null, "https://sub.example.com?tracking_param=value&other=value"))
    }

    @Test
    fun whenCleanTrackingParametersAndInitiatingUrlDomainIsExceptionThenReturnNull() {
        whenever(mockTrackingParametersRepository.exceptions).thenReturn(listOf(FeatureException(domain = "foo.com", reason = "reason")))
        assertNull(testee.cleanTrackingParameters("https://foo.com", URL))
    }

    @Test
    fun whenCleanTrackingParametersAndInitiatingUrlSubdomainIsExceptionThenReturnNull() {
        whenever(mockTrackingParametersRepository.exceptions).thenReturn(listOf(FeatureException(domain = "foo.com", reason = "reason")))
        assertNull(testee.cleanTrackingParameters("https://sub.foo.com", URL))
    }

    @Test
    fun whenCleanTrackingParametersAndUrlHasTrackingParametersThenReturnCleanedUrl() {
        val result = testee.cleanTrackingParameters(null, URL)
        assertEquals(CLEANED_URL, result)
    }

    @Test
    fun whenCleanTrackingParametersAndUrlDoesNotHaveTrackingParametersThenReturnNull() {
        val result = testee.cleanTrackingParameters(null, CLEANED_URL)
        assertNull(result)
    }

    @Test
    fun whenCleanTrackingParametersAndQueryIsValidUrlThenReturnCleanedUrl() {
        val expectedCleanedUrl = "https://example.com?$CLEANED_URL"
        val result = testee.cleanTrackingParameters(null, "https://example.com?$URL")
        assertEquals(expectedCleanedUrl, result)
    }

    @Test
    fun whenCleanTrackingParametersThenSetLastCleanedUrl() {
        assertNull(testee.lastCleanedUrl)
        testee.cleanTrackingParameters(null, URL)
        assertEquals(CLEANED_URL, testee.lastCleanedUrl)
    }

    private fun givenFeatureEnabled(enabled: Boolean) {
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.TrackingParametersFeatureName.value), any())).thenReturn(enabled)
    }

    companion object {
        private const val URL = "https://example.com?tracking_param=value&other=value"
        private const val CLEANED_URL = "https://example.com?other=value"
        private const val TRACKING_PARAMETER = "tracking_param"
    }
}
