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
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ImportPasswordsDesktopAppPromotionParamsTest {

    private val contextMock: Context = mock<Context>().apply {
        whenever(getString(any())).thenReturn("copy")
        whenever(getString(any(), any())).thenReturn("copy with url")
    }

    @Test
    fun whenLaunchedThenTheInteractionHandlerIsRegistered() {
        val params = ImportPasswordsDesktopAppPromotionParams.create(contextMock)

        assertEquals(ImportPasswordsDesktopAppPromotionInteractionHandler.HANDLER_ID, params.handlerId)
    }

    @Test
    fun whenLaunchedThenAttributedUrlIsUnchanged() {
        val params = ImportPasswordsDesktopAppPromotionParams.create(contextMock)

        assertEquals("https://duckduckgo.com/browser?origin=funnel_browser_android_sync", params.link.downloadUrl)
    }

    @Test
    fun whenLaunchedThenNoDismissButton() {
        val params = ImportPasswordsDesktopAppPromotionParams.create(contextMock)

        assertFalse(params.showDismissButton)
    }

    @Test
    fun whenLaunchedThenShareSheetCarriesTheLongerMarketingMessage() {
        val params = ImportPasswordsDesktopAppPromotionParams.create(contextMock)

        assertEquals("copy with url", params.share.shareIntentBody)
    }
}
