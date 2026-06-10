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

package com.duckduckgo.app.browser.favicon

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_PERSISTED_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_WIDGET_PLACEHOLDERS_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.NO_SUBFOLDER
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.widget.WidgetFaviconProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import com.duckduckgo.mobile.android.R as CommonR

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WidgetFaviconProviderTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockFaviconPersister: FaviconPersister = mock()
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockFile: File = File("test")

    private lateinit var testee: WidgetFaviconProvider

    @Before
    fun setup() {
        testee = WidgetFaviconProvider(
            context = context,
            faviconPersister = mockFaviconPersister,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenPersistedFaviconExistsThenReturnItWithoutCheckingPlaceholders() = runTest {
        whenever(mockFaviconPersister.faviconFile(eq(FAVICON_PERSISTED_DIR), any(), any())).thenReturn(mockFile)

        val result = testee.getOrGenerateWidgetFavicon(
            domain = "example.com",
            placeholderSizePx = 128,
            placeholderCornerRadius = CommonR.dimen.searchWidgetFavoritesCornerRadius,
        )

        assertSame(mockFile, result)
        verify(mockFaviconPersister, never()).faviconFile(eq(FAVICON_WIDGET_PLACEHOLDERS_DIR), any(), any())
        verify(mockFaviconPersister, never()).store(any(), any(), any(), any())
    }

    @Test
    fun whenOnlyPlaceholderExistsThenReturnPlaceholder() = runTest {
        whenever(mockFaviconPersister.faviconFile(eq(FAVICON_WIDGET_PLACEHOLDERS_DIR), any(), any())).thenReturn(mockFile)

        val result = testee.getOrGenerateWidgetFavicon(
            domain = "example.com",
            placeholderSizePx = 128,
            placeholderCornerRadius = CommonR.dimen.searchWidgetFavoritesCornerRadius,
        )

        assertSame(mockFile, result)
        verify(mockFaviconPersister, never()).store(any(), any(), any(), any())
    }

    @Test
    fun whenNoFilesExistThenGenerateAndStorePlaceholder() = runTest {
        val storedFile = File("stored")
        whenever(mockFaviconPersister.store(eq(FAVICON_WIDGET_PLACEHOLDERS_DIR), eq(NO_SUBFOLDER), any(), eq("example.com")))
            .thenReturn(storedFile)

        val result = testee.getOrGenerateWidgetFavicon(
            domain = "example.com",
            placeholderSizePx = 128,
            placeholderCornerRadius = CommonR.dimen.searchWidgetFavoritesCornerRadius,
        )

        assertSame(storedFile, result)
        verify(mockFaviconPersister).store(eq(FAVICON_WIDGET_PLACEHOLDERS_DIR), eq(NO_SUBFOLDER), any(), eq("example.com"))
    }
}
