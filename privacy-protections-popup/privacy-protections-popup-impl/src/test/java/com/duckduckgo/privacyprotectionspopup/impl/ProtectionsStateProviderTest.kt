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

package com.duckduckgo.privacyprotectionspopup.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ProtectionsStateProviderTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val featureToggle: FeatureToggle = mock()
    private val contentBlocking: ContentBlocking = mock()
    private val unprotectedTemporary: UnprotectedTemporary = mock()
    private val userAllowListRepository = FakeUserAllowlistRepository()

    private val subject = ProtectionsStateProviderImpl(
        featureToggle = featureToggle,
        contentBlocking = contentBlocking,
        unprotectedTemporary = unprotectedTemporary,
        userAllowListRepository = userAllowListRepository,
    )

    @Before
    fun setUp() {
        whenever(featureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value)).thenReturn(true)
        whenever(contentBlocking.isAnException(any())).thenReturn(false)
        whenever(unprotectedTemporary.isAnException(any())).thenReturn(false)
    }

    @Test
    fun whenContentBlockingIsEnabledAndDomainIsNotAnExceptionThenProtectionsAreEnabled() = runTest {
        assertTrue(areProtectionsEnabled(domain = "www.example.com"))
    }

    @Test
    fun whenContentBlockingFeatureIsDisabledThenProtectionsAreDisabled() = runTest {
        val domain = "www.example.com"
        whenever(featureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value)).thenReturn(false)

        assertFalse(areProtectionsEnabled(domain))
    }

    @Test
    fun whenDomainIsOnContentBlockingExceptionListThenProtectionsAreDisabled() = runTest {
        val domain = "www.example.com"
        whenever(contentBlocking.isAnException(domain)).thenReturn(true)

        assertFalse(areProtectionsEnabled(domain))
    }

    @Test
    fun whenDomainIsOnUnprotectedTemporaryExceptionListThenProtectionsAreDisabled() = runTest {
        val domain = "www.example.com"
        whenever(unprotectedTemporary.isAnException(domain)).thenReturn(true)

        assertFalse(areProtectionsEnabled(domain))
    }

    @Test
    fun whenDomainIsInUserAllowlistThenProtectionsAreDisabled() = runTest {
        val domain = "www.example.com"
        userAllowListRepository.addDomainToUserAllowList(domain)

        assertFalse(areProtectionsEnabled(domain))
    }

    private suspend fun areProtectionsEnabled(domain: String): Boolean =
        subject.areProtectionsEnabled(domain).first()
}
