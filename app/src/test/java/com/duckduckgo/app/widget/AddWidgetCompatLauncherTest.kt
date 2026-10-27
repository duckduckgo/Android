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

package com.duckduckgo.app.widget

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AddWidgetCompatLauncherTest {
    private val defaultAddWidgetLauncher: AddWidgetLauncher = mock()
    private val legacyAddWidgetLauncher: AddWidgetLauncher = mock()
    private val widgetCapabilities: WidgetCapabilities = mock()
    private val context: Context = mock()
    private val testee = AddWidgetCompatLauncher(
        defaultAddWidgetLauncher,
        legacyAddWidgetLauncher,
        widgetCapabilities,
    )

    @Test
    fun whenAutomaticWidgetAddIsNotSupportedThenDelegateToLegacyAddWidgetLauncher() {
        whenever(widgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)

        testee.launchAddWidget(context, WidgetType.SEARCH_ONLY)

        verify(legacyAddWidgetLauncher).launchAddWidget(context, WidgetType.SEARCH_ONLY)
    }

    @Test
    fun whenAutomaticWidgetAddIsSupportedThenDelegateToAppWidgetManagerAddWidgetLauncher() {
        whenever(widgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)

        testee.launchAddWidget(context, WidgetType.SEARCH_ONLY)

        verify(defaultAddWidgetLauncher).launchAddWidget(context, WidgetType.SEARCH_ONLY)
    }
}
