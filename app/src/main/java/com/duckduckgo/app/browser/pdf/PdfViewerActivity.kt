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

import android.os.Bundle
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import androidx.core.view.children
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.databinding.ActivityPdfViewerBinding
import com.duckduckgo.app.browser.mode.InAppNavigation
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.browser.api.ui.BrowserScreens.PdfViewerActivityParams
import com.duckduckgo.browser.api.ui.BrowserScreens.PdfViewerSource
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PdfViewerActivityParams::class)
class PdfViewerActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixel: Pixel

    private val binding: ActivityPdfViewerBinding by viewBinding()

    private lateinit var source: PdfViewerSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        // Default inset is too wide between the back arrow and title on this screen.
        binding.includeToolbar.toolbar.contentInsetStartWithNavigation =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()

        val params = intent.getActivityParams(PdfViewerActivityParams::class.java)
        val cachedFileUri = params?.cachedFileUri.orEmpty()
        val fileName = params?.fileName.orEmpty()
        source = params?.source ?: PdfViewerSource.EXTERNAL_INTENT
        // External-open pixels measure the system "Open with" handler only. In-app opens (Downloads)
        // reuse this screen but must not inflate those counts.
        val isExternalOpen = source == PdfViewerSource.EXTERNAL_INTENT

        supportActionBar?.title = fileName
        // Toolbar title is ellipsized; tapping it reveals the full file name.
        showFullFileNameOnTitleTap(binding.includeToolbar.toolbar, fileName)

        if (savedInstanceState == null) {
            val pdfFragment = DdgPdfViewerFragment()
            pdfFragment.listener = object : DdgPdfViewerFragment.Listener {
                override fun onLoadDocumentSuccess() {
                    if (isExternalOpen) pixel.fire(PdfPixelName.PDF_EXTERNAL_RENDERED)
                }

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

            if (isExternalOpen) {
                pixel.fire(PdfPixelName.PDF_EXTERNAL_OPENED)
                pixel.fire(PdfPixelName.PDF_EXTERNAL_OPENED_DAILY, type = Daily())
                pixel.fire(PdfPixelName.PDF_EXTERNAL_OPENED_UNIQUE, type = Unique())
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateUp()
                }
            },
        )
    }

    private fun showFullFileNameOnTitleTap(toolbar: Toolbar, fileName: String) {
        if (fileName.isEmpty()) return
        toolbar.children.filterIsInstance<TextView>()
            .firstOrNull { it.text == fileName }
            ?.setOnClickListener {
                Toast.makeText(this, fileName, Toast.LENGTH_LONG).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        navigateUp()
        return true
    }

    // External opens have no in-app back stack, so synthesize the browser as parent. In-app opens
    // (Downloads) already have their launching screen beneath, so just finish() back to it.
    private fun navigateUp() {
        if (source == PdfViewerSource.EXTERNAL_INTENT) {
            val browserIntent = BrowserActivity.intent(
                context = this,
                launchSource = InAppNavigation,
            )
            TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(browserIntent)
                .startActivities()
        }
        finish()
    }

    companion object {
        private const val PDF_VIEWER_FRAGMENT_TAG = "pdf_viewer_fragment"
    }
}
