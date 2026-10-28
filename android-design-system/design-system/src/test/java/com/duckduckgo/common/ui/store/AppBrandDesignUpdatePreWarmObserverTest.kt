/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.common.ui.store

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class AppBrandDesignUpdatePreWarmObserverTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val toggles = mock<AppBrandDesignUpdateToggles>()
    private val selfFeature = mock<Toggle>()
    private val badgesFeature = mock<Toggle>()
    private val infoPanelsFeature = mock<Toggle>()
    private val dialogSpacingFeature = mock<Toggle>()
    private val bottomSheetsFeature = mock<Toggle>()
    private val rmfCardsFeature = mock<Toggle>()
    private val dispatcherProvider = mock<DispatcherProvider>()

    private val testee = AppBrandDesignUpdatePreWarmObserver(
        toggles = toggles,
        appCoroutineScope = coroutineRule.testScope,
        dispatcherProvider = dispatcherProvider,
    )

    @Before
    fun setup() {
        whenever(dispatcherProvider.io()).thenReturn(coroutineRule.testDispatcher)
        whenever(toggles.self()).thenReturn(selfFeature)
        whenever(toggles.badges()).thenReturn(badgesFeature)
        whenever(toggles.infoPanels()).thenReturn(infoPanelsFeature)
        whenever(toggles.dialogSpacing()).thenReturn(dialogSpacingFeature)
        whenever(toggles.bottomSheets()).thenReturn(bottomSheetsFeature)
        whenever(toggles.rmfCards()).thenReturn(rmfCardsFeature)
    }

    @Test
    fun whenOnCreateThenBrandFeatureTogglesAreRead() {
        testee.onCreate(mock())

        verify(selfFeature).isEnabled()
        verify(badgesFeature).isEnabled()
        verify(infoPanelsFeature).isEnabled()
        verify(dialogSpacingFeature).isEnabled()
        verify(bottomSheetsFeature).isEnabled()
        verify(rmfCardsFeature).isEnabled()
    }

    @Test
    fun whenOnCreateThenToggleIsReadOnIoDispatcher() {
        testee.onCreate(mock())

        verify(dispatcherProvider).io()
        verify(dispatcherProvider, never()).main()
    }
}
