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
import com.duckduckgo.autofill.impl.importing.promo.PasswordsInBookmarksImportPromotionViewModel.Command.DismissHost
import com.duckduckgo.autofill.impl.importing.promo.PasswordsInBookmarksImportPromotionViewModel.Command.LaunchPasswordsImport
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

class PasswordsInBookmarksImportPromotionViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()

    private val testee = PasswordsInBookmarksImportPromotionViewModel(
        pixel = pixel,
    )

    @Test
    fun `when impression is recorded then fire displayed pixel`() = runTest {
        testee.recordImpression()

        verify(pixel).fire(IMPORT_PROMOTION_DISPLAYED, mapOf("source" to "bookmarks_import"))
    }

    @Test
    fun `when passwords are imported then fire confirmed pixel and launch passwords import`() = runTest {
        testee.commands.test {
            testee.importPasswords()

            assertEquals(LaunchPasswordsImport, awaitItem())
            verify(pixel).fire(IMPORT_PROMOTION_CONFIRMED, mapOf("source" to "bookmarks_import"))
        }
    }

    @Test
    fun `when passwords are imported twice before command is delivered then launch passwords import once`() = runTest {
        testee.importPasswords()
        testee.importPasswords()

        testee.commands.test {
            assertEquals(LaunchPasswordsImport, awaitItem())
            expectNoEvents()
        }
        verify(pixel, times(1)).fire(IMPORT_PROMOTION_CONFIRMED, mapOf("source" to "bookmarks_import"))
    }

    @Test
    fun `when passwords import command is delivered then passwords can be imported again`() = runTest {
        testee.commands.test {
            testee.importPasswords()
            assertEquals(LaunchPasswordsImport, awaitItem())

            testee.importPasswords()
            assertEquals(LaunchPasswordsImport, awaitItem())
        }
    }

    @Test
    fun `when passwords import succeeds then dismiss host`() = runTest {
        testee.commands.test {
            testee.onPasswordsImportFinished(isSuccess = true)

            assertEquals(DismissHost, awaitItem())
        }
    }

    @Test
    fun `when passwords import does not succeed then do not dismiss host`() = runTest {
        testee.commands.test {
            testee.onPasswordsImportFinished(isSuccess = false)

            expectNoEvents()
        }
    }
}
