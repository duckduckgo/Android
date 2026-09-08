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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ImportPasswordsDesktopAppPromotionInteractionHandlerTest {

    private val pixelMock: Pixel = mock()
    private val testee = ImportPasswordsDesktopAppPromotionInteractionHandler(pixelMock)

    @Test
    fun whenShareClickedThenSharePixelFiresAndInteractionIsHandled() = runTest {
        val handled = testee.onInteraction(Interaction.SHARE_CLICKED)

        verify(pixelMock).fire(AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK)
        assertTrue(handled)
    }

    @Test
    fun whenLinkCopiedThenCopyPixelFiresAndInteractionIsHandled() = runTest {
        val handled = testee.onInteraction(Interaction.LINK_COPIED)

        verify(pixelMock).fire(AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK)
        assertTrue(handled)
    }

    @Test
    fun whenImpressionShareCompletedOrDismissedThenNothingFiresAndNoDefaultIsNeeded() = runTest {
        assertTrue(testee.onInteraction(Interaction.IMPRESSION))
        assertTrue(testee.onInteraction(Interaction.SHARE_COMPLETED))
        assertTrue(testee.onInteraction(Interaction.DISMISSED))
    }
}
