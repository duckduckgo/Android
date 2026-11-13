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

package com.duckduckgo.widget

import com.duckduckgo.app.pixels.AppPixelName.SEARCH_WIDGET_ADDED
import com.duckduckgo.app.pixels.AppPixelName.SEARCH_WIDGET_DELETED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.widget.api.HomeScreenWidgetLifecycle
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock

class SearchWidgetLifecycleDelegateTest {

    private val homeScreenWidgetLifecycle: HomeScreenWidgetLifecycle = mock()
    private val pixel: Pixel = mock()
    private val testee = SearchWidgetLifecycleDelegate(homeScreenWidgetLifecycle, pixel)

    @Test
    fun whenWidgetEnabledThenReportsLifecycleAndFiresSpecificPixel() {
        testee.handleOnWidgetEnabled(SEARCH_WIDGET_ADDED)

        inOrder(homeScreenWidgetLifecycle, pixel) {
            verify(homeScreenWidgetLifecycle).onWidgetEnabled()
            verify(pixel).fire(SEARCH_WIDGET_ADDED)
        }
    }

    @Test
    fun whenWidgetDisabledThenReportsLifecycleAndFiresSpecificPixel() {
        testee.handleOnWidgetDisabled(SEARCH_WIDGET_DELETED)

        inOrder(homeScreenWidgetLifecycle, pixel) {
            verify(homeScreenWidgetLifecycle).onWidgetDisabled()
            verify(pixel).fire(SEARCH_WIDGET_DELETED)
        }
    }
}
