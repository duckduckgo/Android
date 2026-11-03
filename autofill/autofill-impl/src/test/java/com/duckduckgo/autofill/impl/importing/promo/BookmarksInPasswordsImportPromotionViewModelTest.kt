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

package com.duckduckgo.autofill.impl.importing.promo

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.importing.promo.BookmarksInPasswordsImportPromotionViewModel.Command.DismissHost
import com.duckduckgo.autofill.impl.importing.promo.BookmarksInPasswordsImportPromotionViewModel.Command.LaunchBookmarksImport
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.IMPORT_PROMOTION_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.IMPORT_PROMOTION_DISPLAYED
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class BookmarksInPasswordsImportPromotionViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()

    private val testee = BookmarksInPasswordsImportPromotionViewModel(
        pixel = pixel,
    )

    @Test
    fun `when impression is recorded then fire displayed pixel`() = runTest {
        testee.recordImpression()

        verify(pixel).fire(IMPORT_PROMOTION_DISPLAYED, mapOf("source" to "passwords_import"))
    }

    @Test
    fun `when bookmarks are imported then fire confirmed pixel and launch bookmarks import`() = runTest {
        testee.commands.test {
            testee.importBookmarks()

            assertEquals(LaunchBookmarksImport, awaitItem())
            verify(pixel).fire(IMPORT_PROMOTION_CONFIRMED, mapOf("source" to "passwords_import"))
        }
    }

    @Test
    fun `when bookmarks are imported twice before command is delivered then launch bookmarks import once`() = runTest {
        testee.importBookmarks()
        testee.importBookmarks()

        testee.commands.test {
            assertEquals(LaunchBookmarksImport, awaitItem())
            expectNoEvents()
        }
        verify(pixel, times(1)).fire(IMPORT_PROMOTION_CONFIRMED, mapOf("source" to "passwords_import"))
    }

    @Test
    fun `when bookmarks import command is delivered then bookmarks can be imported again`() = runTest {
        testee.commands.test {
            testee.importBookmarks()
            assertEquals(LaunchBookmarksImport, awaitItem())

            testee.importBookmarks()
            assertEquals(LaunchBookmarksImport, awaitItem())
        }
    }

    @Test
    fun `when bookmarks import succeeds then dismiss host`() = runTest {
        testee.commands.test {
            testee.onBookmarksImportFinished(isSuccess = true)

            assertEquals(DismissHost, awaitItem())
        }
    }

    @Test
    fun `when bookmarks import does not succeed then do not dismiss host`() = runTest {
        testee.commands.test {
            testee.onBookmarksImportFinished(isSuccess = false)

            expectNoEvents()
        }
    }
}
