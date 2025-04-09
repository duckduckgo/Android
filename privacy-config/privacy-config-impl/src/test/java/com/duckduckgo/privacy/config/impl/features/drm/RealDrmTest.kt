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

package com.duckduckgo.privacy.config.impl.features.drm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.drm.DrmRepository
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDrmTest {

    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockDrmRepository: DrmRepository = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()

    val testee: RealDrm = RealDrm(mockFeatureToggle, mockDrmRepository, mockUserAllowListRepository, mockUnprotectedTemporary)

    @Test
    fun whenIsDrmAllowedForUrlIfFeatureIsEnabledAndProtectedMediaIdIsRequestedThenTrueIsReturned() {
        giveFeatureIsEnabled()
        givenUrlIsInExceptionList()

        val url = "https://open.spotify.com"
        assertTrue(testee.isDrmAllowedForUrl(url))
    }

    @Test
    fun whenIsDrmAllowedForUrlIfFeatureIsEnabledAndDomainIsNotInExceptionsListThenFalseIsReturned() {
        giveFeatureIsEnabled()
        givenUrlIsNotInExceptionList()

        val url = "https://test.com"
        assertFalse(testee.isDrmAllowedForUrl(url))
    }

    @Test
    fun whenIsDrmAllowedForUrlIfFeatureIsDisableThenFalseIsReturned() {
        val url = "https://open.spotify.com"

        assertFalse(testee.isDrmAllowedForUrl(url))
    }

    @Test
    fun whenIsDrmAllowedForUrlAndIsInUserAllowListThenTrueIsReturned() {
        giveFeatureIsEnabled()
        givenUrlIsNotInExceptionList()
        givenUriIsInUserAllowList()

        val url = "https://open.spotify.com"
        assertTrue(testee.isDrmAllowedForUrl(url))
    }

    @Test
    fun whenIsDrmAllowedForUrlAndIsInUnprotectedTemporaryThenTrueIsReturned() {
        giveFeatureIsEnabled()
        givenUrlIsNotInExceptionList()
        givenUrlIsInUnprotectedTemporary()

        val url = "https://open.spotify.com"
        assertTrue(testee.isDrmAllowedForUrl(url))
    }

    private fun giveFeatureIsEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.DrmFeatureName.value), any())).thenReturn(true)
    }

    private fun givenUrlIsInExceptionList() {
        val exceptions = CopyOnWriteArrayList<FeatureException>().apply { add(FeatureException("open.spotify.com", "my reason here")) }
        whenever(mockDrmRepository.exceptions).thenReturn(exceptions)
    }

    private fun givenUrlIsNotInExceptionList() {
        whenever(mockDrmRepository.exceptions).thenReturn(CopyOnWriteArrayList<FeatureException>())
    }

    private fun givenUriIsInUserAllowList() {
        whenever(mockUserAllowListRepository.isUriInUserAllowList(any())).thenReturn(true)
    }

    private fun givenUrlIsInUnprotectedTemporary() {
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(true)
    }
}
