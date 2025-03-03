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

package com.duckduckgo.tabs.tabs.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter
import com.duckduckgo.app.tabs.ui.TabSwitcherListener
import com.duckduckgo.app.tabs.ui.TrackerCountAnimator
import com.duckduckgo.common.utils.DispatcherProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class TabSwitcherAdapterTestAndroid {

    private val mockTabSwitchListener: TabSwitcherListener = mock()
    private val testee: TabSwitcherAdapter = createMockTabSwitcherAdapter(
        listener = mockTabSwitchListener,
    )

    @Test
    fun `close click listener returns current position`() {
        var currentPosition = 5
        val positionProvider = { currentPosition }

        val clickListener = testee.createCloseClickListener(positionProvider, mockTabSwitchListener)

        clickListener.onClick(mock())
        verify(mockTabSwitchListener).onTabDeleted(5, false)

        currentPosition = 3

        clickListener.onClick(mock())
        verify(mockTabSwitchListener).onTabDeleted(3, false)
    }

    private fun createMockTabSwitcherAdapter(
        listener: TabSwitcherListener,
    ): TabSwitcherAdapter {
        return TabSwitcherAdapter(
            itemClickListener = listener,
            webViewPreviewPersister = mock<WebViewPreviewPersister>(),
            lifecycleOwner = mock(),
            faviconManager = mock<FaviconManager>(),
            dispatchers = mock<DispatcherProvider>(),
            trackerCountAnimator = mock<TrackerCountAnimator>(),
        )
    }
}
