/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.user.agent.store

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RealUserAgentFeatureToggleRepositoryTest {

    lateinit var testee: RealUserAgentFeatureToggleRepository

    private val mockPrivacyFeatureTogglesStore: UserAgentFeatureToggleStore = mock()

    @Before
    fun before() {
        testee = RealUserAgentFeatureToggleRepository(mockPrivacyFeatureTogglesStore)
    }

    @Test
    fun whenDeleteAllThenDeleteAllCalled() {
        testee.deleteAll()

        verify(mockPrivacyFeatureTogglesStore).deleteAll()
    }

    @Test
    fun whenGetThenGetCalled() {
        testee.get(UserAgentFeatureName.UserAgent, true)

        verify(mockPrivacyFeatureTogglesStore).get(UserAgentFeatureName.UserAgent, true)
    }

    @Test
    fun whenInsertThenInsertCalled() {
        val privacyFeatureToggle = UserAgentFeatureToggle(UserAgentFeatureName.UserAgent.value, true, null)
        testee.insert(privacyFeatureToggle)

        verify(mockPrivacyFeatureTogglesStore).insert(privacyFeatureToggle)
    }
}
