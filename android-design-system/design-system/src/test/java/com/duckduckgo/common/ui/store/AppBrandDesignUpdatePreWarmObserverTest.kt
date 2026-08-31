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
    private val themeFeature = mock<Toggle>()
    private val addressBarFeature = mock<Toggle>()
    private val pictogramsFeature = mock<Toggle>()
    private val dispatcherProvider = mock<DispatcherProvider>()

    private val testee = AppBrandDesignUpdatePreWarmObserver(
        toggles = toggles,
        appCoroutineScope = coroutineRule.testScope,
        dispatcherProvider = dispatcherProvider,
    )

    @Before
    fun setup() {
        whenever(dispatcherProvider.io()).thenReturn(coroutineRule.testDispatcher)
        whenever(toggles.theme()).thenReturn(themeFeature)
        whenever(toggles.addressBar()).thenReturn(addressBarFeature)
        whenever(toggles.pictograms()).thenReturn(pictogramsFeature)
    }

    @Test
    fun whenOnCreateThenBrandFeatureTogglesAreRead() {
        testee.onCreate(mock())

        verify(themeFeature).isEnabled()
        verify(addressBarFeature).isEnabled()
        verify(pictogramsFeature).isEnabled()
    }

    @Test
    fun whenOnCreateThenToggleIsReadOnIoDispatcher() {
        testee.onCreate(mock())

        verify(dispatcherProvider).io()
        verify(dispatcherProvider, never()).main()
    }
}
