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
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionParams
import com.duckduckgo.desktopapppromotion.api.PixelConfig
import com.duckduckgo.desktopapppromotion.api.PixelFireSpec
import com.duckduckgo.mobile.android.R as CommonR

/**
 * No impression or dismiss pixel, and no interaction handler: this entry point tracks neither, and
 * has no dismissal to persist.
 */
object ImportPasswordsDesktopAppPromotionParams {

    fun create(context: Context): DesktopAppPromotionParams = DesktopAppPromotionParams(
        toolbarTitle = context.getString(R.string.autofillManagementImportPasswordsGetDesktopAppTitle),
        title = context.getString(R.string.autofillManagementImportPasswordsGetDesktopAppSubtitle),
        body = context.getString(R.string.autofillManagementImportPasswordsGetDesktopAppInstruction),
        illustration = CommonR.drawable.ic_app_download_128,
        downloadUrl = DESKTOP_APP_URL,
        shareIntentTitle = context.getString(R.string.autofillManagementImportPasswordsGetDesktopBrowserIntentTitle),
        shareIntentBody = context.getString(R.string.autofillManagementImportPasswordsGetDesktopBrowserIntentMessage, DESKTOP_APP_URL),
        showDismissButton = false,
        pixels = PixelConfig(
            shareClicked = PixelFireSpec(AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK.pixelName),
            linkClicked = PixelFireSpec(AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK.pixelName),
        ),
    )

    // Preserved verbatim from the screen this replaces, sync attribution and all, so funnel
    // reporting is unchanged by the consolidation.
    private const val DESKTOP_APP_URL = "https://duckduckgo.com/browser?origin=funnel_browser_android_sync"
}
