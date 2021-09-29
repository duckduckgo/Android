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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.ContentBlockingException
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingRepository
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealContentBlockingTest {

    lateinit var testee: RealContentBlocking

    private val mockContentBlockingRepository: ContentBlockingRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockFeatureToggle: FeatureToggle = mock()

    @Before
    fun before() {
        givenFeatureIsEnabled()

        testee = RealContentBlocking(mockContentBlockingRepository, mockFeatureToggle, mockUnprotectedTemporary)
    }

    @Test
    fun whenIsAnExceptionAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isAnException("http://www.example.com"))
    }

    @Test
    fun whenIsAnExceptionWithSubdomainAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isAnException("http://test.example.com"))
    }

    @Test
    fun whenIsAnExceptionAndDomainIsNotListedInTheExceptionsListThenReturnFalse() {
        whenever(mockContentBlockingRepository.exceptions).thenReturn(arrayListOf())

        assertFalse(testee.isAnException("http://test.example.com"))
    }

    @Test
    fun whenIsAnExceptionAndDomainIsInTheUnprotectedTemporaryListThenReturnTrue() {
        val url = "http://test.example.com"
        whenever(mockUnprotectedTemporary.isAnException(url)).thenReturn(true)
        whenever(mockContentBlockingRepository.exceptions).thenReturn(arrayListOf())

        assertTrue(testee.isAnException(url))
    }

    @Test
    fun whenIsAnExceptionAndFeatureIsDisabledThenReturnFalse() {
        givenThereAreExceptions()
        givenFeatureIsDisabled()

        assertFalse(testee.isAnException("http://test.example.com"))
    }

    private fun givenThereAreExceptions() {
        whenever(mockContentBlockingRepository.exceptions).thenReturn(
            arrayListOf(
                ContentBlockingException("example.com", "my reason here")
            )
        )
    }

    private fun givenFeatureIsEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName(), true)).thenReturn(true)
    }

    private fun givenFeatureIsDisabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName(), true)).thenReturn(false)
    }
}
