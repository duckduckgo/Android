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

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Pixel names reach the shared promo screen as plain strings, so these assert the exact wire names
 * this entry point fired before the screens were consolidated.
 */
class ImportPasswordsDesktopAppPromotionParamsTest {

    private val contextMock: Context = mock<Context>().apply {
        whenever(getString(any())).thenReturn("copy")
        whenever(getString(any(), any())).thenReturn("copy with url")
    }

    @Test
    fun whenLaunchedThenShareAndLinkPixelsAreUnchanged() {
        val params = ImportPasswordsDesktopAppPromotionParams.create(contextMock)

        assertEquals("m_get_desktop_share", params.pixels.shareClicked?.pixelName)
        assertEquals("m_get_desktop_copy", params.pixels.linkClicked?.pixelName)
    }

    @Test
    fun whenLaunchedThenNoImpressionOrDismissPixelIsConfigured() {
        val params = ImportPasswordsDesktopAppPromotionParams.create(contextMock)

        assertNull(params.pixels.impression)
        assertNull(params.pixels.dismissed)
    }

    @Test
    fun whenLaunchedThenAttributedUrlIsUnchanged() {
        val params = ImportPasswordsDesktopAppPromotionParams.create(contextMock)

        assertEquals("https://duckduckgo.com/browser?origin=funnel_browser_android_sync", params.downloadUrl)
    }

    @Test
    fun whenLaunchedThenNoDismissButtonAndNoInteractionHandler() {
        val params = ImportPasswordsDesktopAppPromotionParams.create(contextMock)

        assertFalse(params.showDismissButton)
        assertNull(params.handlerId)
    }

    @Test
    fun whenLaunchedThenShareSheetCarriesTheLongerMarketingMessage() {
        val params = ImportPasswordsDesktopAppPromotionParams.create(contextMock)

        assertEquals("copy with url", params.shareIntentBody)
    }
}
