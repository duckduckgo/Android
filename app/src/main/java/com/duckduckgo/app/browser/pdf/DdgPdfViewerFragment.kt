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

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.pdf.viewer.fragment.PdfViewerFragmentV2
import com.duckduckgo.app.browser.R

/**
 * Thin subclass of [PdfViewerFragmentV2] that applies a DDG-flavored Material3 theme overlay
 * and exposes a public method to set the document URI.
 *
 * The URI must be set AFTER the fragment is attached — `PdfViewerFragmentV2.setDocumentUri`
 * internally resolves a `viewModels()` delegate, which throws `IllegalStateException` if the
 * fragment is detached.
 */
@SuppressLint("RestrictedApi")
class DdgPdfViewerFragment : PdfViewerFragmentV2() {

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val themedContext = ContextThemeWrapper(
            inflater.context,
            R.style.Widget_DuckDuckGo_PdfViewer,
        )
        return inflater.cloneInContext(themedContext)
    }

    fun documentUri(uri: Uri) {
        documentUri = uri
    }
}
