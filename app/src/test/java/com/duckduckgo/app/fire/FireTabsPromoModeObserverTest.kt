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

package com.duckduckgo.app.fire

import com.duckduckgo.app.fire.promo.FireTabsPromos
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class FireTabsPromoModeObserverTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val currentMode = MutableStateFlow(BrowserMode.REGULAR)
    private val browserModeStateHolder: BrowserModeStateHolder = mock {
        on { it.currentMode } doReturn currentMode
    }
    private val fireTabsPromos: FireTabsPromos = mock()

    private val testee = FireTabsPromoModeObserver(
        browserModeStateHolder = browserModeStateHolder,
        fireTabsPromos = fireTabsPromos,
        appCoroutineScope = coroutineRule.testScope,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenModeBecomesFireThenOnFireModeEnteredCalled() = runTest {
        testee.onCreate(mock())
        currentMode.value = BrowserMode.FIRE
        verify(fireTabsPromos).onFireModeEntered()
    }

    @Test
    fun whenModeStaysRegularThenOnFireModeEnteredNotCalled() = runTest {
        testee.onCreate(mock())
        verify(fireTabsPromos, never()).onFireModeEntered()
    }
}
