/*
 * Copyright (c) 2025 DuckDuckGo
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

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentPdfBinding
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.FragmentScope
import com.rajat.pdfviewer.PdfRendererView
import logcat.logcat

@InjectWith(FragmentScope::class)
class PdfFragment : DuckDuckGoFragment(R.layout.fragment_pdf) {

    private val binding by viewBinding<FragmentPdfBinding>()

    var onPdfLoadSuccess: ((String) -> Unit)? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        requireArguments().getString(KEY_PDF_URL)!!.let { url ->
            binding.pdfView.statusListener = object : PdfRendererView.StatusCallBack {
                override fun onPdfLoadSuccess(absolutePath: String) {
                    logcat { "PDF loaded successfully: $absolutePath" }
                    onPdfLoadSuccess?.invoke(absolutePath)
                }

                override fun onError(error: Throwable) {
                    logcat { "PDF load failed: ${error.message}" }
                }
            }

            binding.pdfView.initWithUrl(
                url = url,
                lifecycleCoroutineScope = lifecycleScope,
                lifecycle = lifecycle,
            )
        }
    }

    companion object {

        const val TAG_PDF_FRAGMENT = "pdfFragment"
        const val MIME_TYPE_PDF = "application/pdf"
        private const val KEY_PDF_URL = "KEY_PDF_URI"

        fun newInstance(url: String): PdfFragment =
            PdfFragment().apply {
                arguments = bundleOf(KEY_PDF_URL to url)
            }
    }
}
