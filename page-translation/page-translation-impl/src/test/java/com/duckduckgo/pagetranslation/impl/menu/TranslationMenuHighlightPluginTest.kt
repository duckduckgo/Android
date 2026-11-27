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

package com.duckduckgo.pagetranslation.impl.menu

import com.duckduckgo.app.browser.menu.BrowserViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class TranslationMenuHighlightPluginTest {

    private val shown = MutableStateFlow<TranslationMenuViewState>(TranslationMenuViewState.Hidden)
    private val visibleTab: VisibleTabHolder = mock { on { viewState } doReturn shown }
    private val testee = TranslationMenuHighlightPlugin(visibleTab)

    private suspend fun highlighted(mode: BrowserViewMode = BrowserViewMode.Browser) = testee.isHighlighted(mode).first()

    @Test
    fun whenPageIsTranslatedThenHighlighted() = runTest {
        shown.value = TranslationMenuViewState.ShowOriginal

        assertTrue(highlighted())
    }

    @Test
    fun whenOnlyTheTranslateRowIsShownThenNotHighlighted() = runTest {
        listOf(
            TranslationMenuViewState.Hidden,
            TranslationMenuViewState.Translate("en", enabled = true),
            TranslationMenuViewState.Translate("en", enabled = false),
            TranslationMenuViewState.InProgress,
        )
            .forEach {
                shown.value = it
                assertFalse(it.toString(), highlighted())
            }
    }

    @Test
    fun whenInACustomTabThenNotHighlighted() = runTest {
        shown.value = TranslationMenuViewState.ShowOriginal

        assertFalse(highlighted(BrowserViewMode.CustomTab))
    }
}
