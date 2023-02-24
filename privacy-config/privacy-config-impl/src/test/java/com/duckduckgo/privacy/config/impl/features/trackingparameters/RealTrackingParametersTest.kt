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

import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersRepository
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealTrackingParametersTest {

    private lateinit var testee: RealTrackingParameters
    private val mockTrackingParametersRepository: TrackingParametersRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()

    @Before
    fun setup() {
        testee = RealTrackingParameters(mockTrackingParametersRepository, mockFeatureToggle, mockUnprotectedTemporary, mockUserAllowListRepository)
    }

    @Test
    fun whenIsExceptionCalledAndDomainIsInUserAllowListThenReturnTrue() {
        whenever(mockUserAllowListRepository.isUrlInUserAllowList(anyString())).thenReturn(true)
        assertTrue(testee.isAnException("foo.com", "test.com"))
    }
}
