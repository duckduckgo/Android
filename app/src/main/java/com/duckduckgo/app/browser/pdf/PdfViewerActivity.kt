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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.databinding.ActivityPdfViewerBinding
import com.duckduckgo.app.browser.mode.InAppNavigation
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class PdfViewerActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixel: Pixel

    private val binding: ActivityPdfViewerBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        val cachedFileUri = intent.getStringExtra(EXTRA_CACHED_URI).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()

        supportActionBar?.title = fileName

        if (savedInstanceState == null) {
            val pdfFragment = DdgPdfViewerFragment()
            pdfFragment.errorListener = object : DdgPdfViewerFragment.ErrorListener {
                override fun onLoadDocumentError(throwable: Throwable) {
                    pixel.fire(
                        PdfPixelName.PDF_RENDER_FAILURE,
                        parameters = mapOf("error_type" to PdfErrorType.UNKNOWN.paramValue),
                    )
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(binding.pdfViewerContainer.id, pdfFragment, PDF_VIEWER_FRAGMENT_TAG)
                .commitNow()
            pdfFragment.documentUri(cachedFileUri.toUri())

            pixel.fire(PdfPixelName.PDF_EXTERNAL_OPENED)
            pixel.fire(PdfPixelName.PDF_EXTERNAL_OPENED_DAILY, type = Daily())
            pixel.fire(PdfPixelName.PDF_EXTERNAL_OPENED_UNIQUE, type = Unique())
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateToBrowser()
                }
            },
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        navigateToBrowser()
        return true
    }

    private fun navigateToBrowser() {
        val browserIntent = BrowserActivity.intent(
            context = this,
            launchSource = InAppNavigation,
        )
        TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(browserIntent)
            .startActivities()
        finish()
    }

    companion object {
        private const val EXTRA_CACHED_URI = "extra_cached_uri"
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val PDF_VIEWER_FRAGMENT_TAG = "pdf_viewer_fragment"

        fun intent(
            context: Context,
            cachedFileUri: String,
            fileName: String,
        ): Intent {
            return Intent(context, PdfViewerActivity::class.java).apply {
                putExtra(EXTRA_CACHED_URI, cachedFileUri)
                putExtra(EXTRA_FILE_NAME, fileName)
            }
        }
    }
}
