/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.brokensite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.Command
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.ViewState
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.content_broken_sites.*
import kotlinx.android.synthetic.main.include_toolbar.*
import org.jetbrains.anko.longToast


class BrokenSiteActivity : DuckDuckGoActivity() {
    private val viewModel: BrokenSiteViewModel by bindViewModel()
    val categories: Array<String> by lazy { resources.getStringArray(R.array.brokenSitesCategories) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_broken_site)
        configureListeners()
        configureObservers()
        setupActionBar()
        if (savedInstanceState == null) {
            consumeIntentExtra()
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun consumeIntentExtra() {
        val url = intent.getStringExtra(URL_EXTRA)
        val blockedTrackers = intent.getStringExtra(BLOCKED_TRACKERS_EXTRA)
        val upgradedHttps = intent.getBooleanExtra(UPGRADED_TO_HTTPS_EXTRA, false)
        viewModel.setInitialBrokenSite(url, blockedTrackers, upgradedHttps)
    }

    private fun configureListeners() {
        categoriesSelection.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.brokenSitesCategoriesTitle))
                .setSingleChoiceItems(categories, -1) { _, newIndex ->
                    viewModel.onCategoryIndexChanged(newIndex)
                }
                .setPositiveButton(getString(android.R.string.yes)) { dialog, _ -> dialog.dismiss() }
                .setNegativeButton(getString(android.R.string.no)) { dialog, _ -> dialog.dismiss() }
                .show()
        }

        submitButton.setOnClickListener {
            val webViewVersion = WebViewCompat.getCurrentWebViewPackage(applicationContext)?.versionName ?: BrokenSiteViewModel.UNKNOWN_VERSION
            val categoryValues = resources.getStringArray(R.array.brokenSitesValues)
            viewModel.onSubmitPressed(webViewVersion, categoryValues[viewModel.indexSelected()])
        }
    }

    private fun configureObservers() {
        viewModel.command.observe(this, Observer {
            it?.let { processCommand(it) }
        })
        viewModel.viewState.observe(this, Observer {
            it?.let { render(it) }
        })
    }

    private fun processCommand(command: Command) {
        when (command) {
            Command.ConfirmAndFinish -> confirmAndFinish()
        }
    }

    private fun confirmAndFinish() {
        longToast(R.string.brokenSiteSubmitted)
        finishAfterTransition()
    }

    private fun render(viewState: ViewState) {
        val category = if (viewState.indexSelected > -1) categories[viewState.indexSelected] else ""
        categoriesSelection.setText(category)
        submitButton.isEnabled = viewState.submitAllowed
    }

    companion object {

        private const val URL_EXTRA = "URL_EXTRA"
        private const val BLOCKED_TRACKERS_EXTRA = "BLOCKED_TRACKERS_EXTRA"
        private const val UPGRADED_TO_HTTPS_EXTRA = "UPGRADED_TO_HTTPS_EXTRA"

        fun intent(context: Context, url: String? = null, blockedTrackers: String?, upgradedToHttps: Boolean): Intent {
            val intent = Intent(context, BrokenSiteActivity::class.java)
            if (url != null) {
                intent.putExtra(URL_EXTRA, url)
            }
            if (blockedTrackers != null) {
                intent.putExtra(BLOCKED_TRACKERS_EXTRA, blockedTrackers)
            }
            intent.putExtra(UPGRADED_TO_HTTPS_EXTRA, upgradedToHttps)
            return intent
        }
    }
}
