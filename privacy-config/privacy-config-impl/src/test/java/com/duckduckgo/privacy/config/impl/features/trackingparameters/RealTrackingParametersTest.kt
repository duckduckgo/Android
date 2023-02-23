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

import android.net.Uri
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.userwhitelist.api.UserWhiteListRepository
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersRepository
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealTrackingParametersTest {

    private lateinit var testee: RealTrackingParameters
    private val mockTrackingParametersRepository: TrackingParametersRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockUserWhiteListRepository: UserWhiteListRepository = mock()
    private val mockUri: Uri = mock()

    @Before
    fun setup() {
        testee = RealTrackingParameters(mockTrackingParametersRepository, mockFeatureToggle, mockUnprotectedTemporary, mockUserWhiteListRepository)
    }

    @Test
    fun whenIsExceptionCalledAndDomainIsInUserAllowListThenReturnTrue() {
        whenever(mockUri.domain()).thenReturn("test.com")
        whenever(mockUserWhiteListRepository.userWhiteList).thenReturn(listOf("test.com"))
        assertTrue(testee.isAnException("foo.com", "test.com", mockUri))
    }
}
