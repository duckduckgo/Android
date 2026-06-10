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

package com.duckduckgo.app.browser.pdf

import android.net.Uri
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Decides how an externally-launched "view PDF" intent (e.g. opening a local PDF from a file
 * manager via the Android app picker) should be handled by [PdfViewerActivity].
 */
interface ExternalPdfViewDecider {
    fun decideForView(uri: Uri?): ExternalPdfViewDecision
}

sealed class ExternalPdfViewDecision {
    /** Render the document in-app with [DdgPdfViewerFragment]. */
    data class Render(val uri: Uri) : ExternalPdfViewDecision()

    /**
     * The document can't be rendered in-app (the androidx pdf viewer requires Android 12+), so
     * hand it back to the system, letting the user pick another viewer instead.
     */
    data class DelegateToOtherApps(val uri: Uri) : ExternalPdfViewDecision()

    /** No usable document was supplied — nothing to do. */
    data object NothingToRender : ExternalPdfViewDecision()
}

@ContributesBinding(AppScope::class)
class RealExternalPdfViewDecider @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : ExternalPdfViewDecider {

    override fun decideForView(uri: Uri?): ExternalPdfViewDecision {
        if (uri == null) return ExternalPdfViewDecision.NothingToRender
        return if (appBuildConfig.sdkInt >= MIN_PDF_VIEWER_SDK) {
            ExternalPdfViewDecision.Render(uri)
        } else {
            ExternalPdfViewDecision.DelegateToOtherApps(uri)
        }
    }

    companion object {
        // androidx.pdf's PdfViewerFragmentV2 is only available on API 31+.
        private const val MIN_PDF_VIEWER_SDK = 31
    }
}
