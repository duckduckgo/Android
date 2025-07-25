/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar.model

import com.duckduckgo.app.browser.omnibar.model.OmnibarType.SCROLLING
import com.duckduckgo.app.browser.omnibar.model.OmnibarType.SINGLE
import com.duckduckgo.common.ui.experiments.visual.store.ExperimentalThemingDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OmnibarTypeResolverTest {

    private lateinit var testee: OmnibarTypeResolver
    private val mockExperimentDataStore: ExperimentalThemingDataStore = mock()
    private val mockIsNewDesignWithoutBottomBarEnabled = mock<MutableStateFlow<Boolean>>()

    @Before
    fun setup() {
        whenever(mockExperimentDataStore.isSingleOmnibarEnabled).thenReturn(mockIsNewDesignWithoutBottomBarEnabled)
        testee = OmnibarTypeResolver(mockExperimentDataStore)
    }

    @Test
    fun whenSingleOmnibarIsEnabledThenOmnibarTypeIsSingle() {
        whenever(mockIsNewDesignWithoutBottomBarEnabled.value).thenReturn(true)

        val omnibarType = testee.getOmnibarType()

        assertEquals(SINGLE, omnibarType)
    }

    @Test
    fun whenNoExperimentIsEnabledThenOmnibarTypeIsScrolling() {
        whenever(mockIsNewDesignWithoutBottomBarEnabled.value).thenReturn(false)

        val omnibarType = testee.getOmnibarType()

        assertEquals(SCROLLING, omnibarType)
    }
}
