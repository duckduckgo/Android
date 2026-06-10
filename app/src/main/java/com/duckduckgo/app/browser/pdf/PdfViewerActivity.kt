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

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Hosts the in-app PDF viewer for documents opened from outside the browser — e.g. when the user
 * picks DuckDuckGo from the Android "Open with" picker for a local PDF.
 *
 * Registered in the manifest for `ACTION_VIEW` of `application/pdf` documents with `content`/`file`
 * schemes, which is what makes the app appear as a PDF-opener option. Web PDFs (http/https) are
 * still handled by the browser's inline viewer and intentionally not claimed here.
 */
@InjectWith(ActivityScope::class)
class PdfViewerActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var externalPdfViewDecider: ExternalPdfViewDecider

    @Inject
    lateinit var pixel: Pixel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)
        // Only act on a freshly delivered intent; on recreation (e.g. rotation) the fragment
        // restores itself and re-firing would double-count pixels and rebuild the viewer.
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (val decision = externalPdfViewDecider.decideForView(intent?.data)) {
            is ExternalPdfViewDecision.Render -> renderPdf(decision.uri)
            is ExternalPdfViewDecision.DelegateToOtherApps -> {
                delegateToOtherApps(decision.uri)
                finish()
            }
            is ExternalPdfViewDecision.NothingToRender -> {
                logcat(WARN) { "PdfViewerActivity launched without a document to render" }
                finish()
            }
        }
    }

    private fun renderPdf(uri: Uri) {
        if (supportFragmentManager.findFragmentByTag(PDF_VIEWER_FRAGMENT_TAG) != null) return

        val pdfFragment = DdgPdfViewerFragment()
        pdfFragment.errorListener = object : DdgPdfViewerFragment.ErrorListener {
            override fun onLoadDocumentError(throwable: Throwable) {
                onRenderError(throwable)
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.pdfViewerActivityContainer, pdfFragment, PDF_VIEWER_FRAGMENT_TAG)
            .commitNow()
        // documentUri must be set AFTER the fragment is attached — PdfViewerFragmentV2's setter
        // resolves a viewModels() delegate, which throws IllegalStateException if detached.
        pdfFragment.documentUri(uri)

        pixel.fire(PdfPixelName.PDF_VIEWER_OPENED)
        pixel.fire(PdfPixelName.PDF_VIEWER_OPENED_DAILY, type = Daily())
        pixel.fire(PdfPixelName.PDF_VIEWER_OPENED_UNIQUE, type = Unique())
    }

    private fun onRenderError(throwable: Throwable) {
        logcat(WARN) { "Failed to render externally opened PDF: ${throwable.message}" }
        pixel.fire(
            PdfPixelName.PDF_RENDER_FAILURE,
            parameters = mapOf("error_type" to PdfErrorType.UNKNOWN.paramValue),
        )
        Toast.makeText(this, R.string.pdfViewerExternalLoadError, Toast.LENGTH_LONG).show()
        finish()
    }

    /**
     * Re-offers the document to the system, excluding ourselves so the user can choose a viewer
     * that actually supports their device. Used when the in-app viewer is unavailable.
     */
    private fun delegateToOtherApps(uri: Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PDF_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(viewIntent, null).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(this@PdfViewerActivity, PdfViewerActivity::class.java)),
            )
        }
        try {
            startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "No app available to open PDF: ${e.message}" }
            Toast.makeText(this, R.string.pdfViewerExternalNoAppError, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val PDF_VIEWER_FRAGMENT_TAG = "EXTERNAL_PDF_VIEWER_FRAGMENT"
        private const val PDF_MIME_TYPE = "application/pdf"
    }
}
