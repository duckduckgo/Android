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

package com.duckduckgo.user.agent.impl

import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandHintDomain
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandHintFeature
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandHintFeatureSettingsRepository
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandsHints
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList

class RealClientBrandHintProviderTest {

    private val mockFeature: ClientBrandHintFeature = mock()
    private val mockRepository: ClientBrandHintFeatureSettingsRepository = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockToggle: com.duckduckgo.feature.toggles.api.Toggle = mock()

    private lateinit var testee: RealClientBrandHintProvider

    @Before
    fun setup() {
        whenever(mockFeature.self()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockRepository.clientBrandHints).thenReturn(CopyOnWriteArrayList())
        testee = RealClientBrandHintProvider(mockFeature, mockRepository, mockUserAllowListRepository)
    }

    @Test
    fun whenDomainIsInUserAllowListThenShouldChangeBrandingToChrome() {
        whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.audible.com")).thenReturn(true)

        val shouldChange = testee.shouldChangeBranding("https://www.audible.com")

        assertTrue(shouldChange)
    }

    @Test
    fun whenDomainIsNotInUserAllowListThenShouldNotChangeBrandingFromDefault() {
        whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.audible.com")).thenReturn(false)

        val shouldChange = testee.shouldChangeBranding("https://www.audible.com")

        assertFalse(shouldChange)
    }

    @Test
    fun whenDomainIsInUserAllowListAndAlreadyChromeBrandedThenShouldNotChangeBranding() {
        whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(false)
        whenever(mockRepository.clientBrandHints).thenReturn(
            CopyOnWriteArrayList(listOf(ClientBrandHintDomain("www.example.com", ClientBrandsHints.CHROME))),
        )

        testee.shouldChangeBranding("https://www.example.com")

        whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.audible.com")).thenReturn(true)
        val shouldChange = testee.shouldChangeBranding("https://www.audible.com")

        assertFalse(shouldChange)
    }

    @Test
    fun whenFeatureDisabledThenUserAllowListDoesNotAffectBranding() {
        whenever(mockToggle.isEnabled()).thenReturn(false)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.audible.com")).thenReturn(true)

        val shouldChange = testee.shouldChangeBranding("https://www.audible.com")

        assertTrue(shouldChange)
    }
}
