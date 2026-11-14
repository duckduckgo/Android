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

package com.duckduckgo.duckchat.impl.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.widget.api.HomeScreenWidgetLifecycle
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetProviderTest {

    private val pixel: Pixel = mock()
    private val homeScreenWidgetLifecycle: HomeScreenWidgetLifecycle = mock()
    private val testee = DuckAiWidgetProvider().also {
        it.pixel = pixel
        it.homeScreenWidgetLifecycle = homeScreenWidgetLifecycle
    }

    @Test
    fun whenFirstWidgetPlacedThenFiresAddedPixelAndReportsEnabled() {
        testee.onEnabled(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)

        verify(pixel).fire(DuckChatPixelName.DUCK_AI_WIDGET_ADDED)
        verify(homeScreenWidgetLifecycle).onWidgetEnabled()
    }

    @Test
    fun whenLastWidgetRemovedThenFiresDeletedPixelAndReportsDisabled() {
        testee.onDisabled(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)

        verify(pixel).fire(DuckChatPixelName.DUCK_AI_WIDGET_DELETED)
        verify(homeScreenWidgetLifecycle).onWidgetDisabled()
    }
}
