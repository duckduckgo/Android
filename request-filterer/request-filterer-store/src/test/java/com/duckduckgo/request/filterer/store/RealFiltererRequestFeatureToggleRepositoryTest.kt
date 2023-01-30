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

package com.duckduckgo.request.filterer.store

import com.duckduckgo.request.filterer.api.RequestFiltererFeatureName.RequestFilterer
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RealFiltererRequestFeatureToggleRepositoryTest {
    lateinit var testee: RequestFiltererFeatureToggleRepository

    private val mockRequestFiltererFeatureToggleStore: RequestFiltererFeatureToggleRepository = mock()

    @Before
    fun before() {
        testee = RealRequestFiltererFeatureToggleRepository(mockRequestFiltererFeatureToggleStore)
    }

    @Test
    fun whenDeleteAllThenDeleteAllCalled() {
        testee.deleteAll()

        verify(mockRequestFiltererFeatureToggleStore).deleteAll()
    }

    @Test
    fun whenGetThenGetCalled() {
        testee.get(RequestFilterer, true)

        verify(mockRequestFiltererFeatureToggleStore).get(RequestFilterer, true)
    }

    @Test
    fun whenInsertThenInsertCalled() {
        val requestFiltererFeatureToggle = RequestFiltererFeatureToggles(RequestFilterer, true, null)
        testee.insert(requestFiltererFeatureToggle)

        verify(mockRequestFiltererFeatureToggleStore).insert(requestFiltererFeatureToggle)
    }
}
