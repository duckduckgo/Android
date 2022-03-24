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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.Command
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.ViewState
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.WebViewVersionProvider
import com.duckduckgo.app.browser.databinding.ActivityBrokenSiteBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import javax.inject.Inject

class BrokenSiteActivity : DuckDuckGoActivity() {

    private val binding: ActivityBrokenSiteBinding by viewBinding()
    private val viewModel: BrokenSiteViewModel by bindViewModel()
    @Inject lateinit var webViewVersionProvider: WebViewVersionProvider

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val brokenSites
        get() = binding.contentBrokenSites

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureListeners()
        configureObservers()
        setupToolbar(toolbar)
        if (savedInstanceState == null) {
            consumeIntentExtra()
        }
    }

    private fun consumeIntentExtra() {
        val url = intent.getStringExtra(URL_EXTRA).orEmpty()
        val blockedTrackers = intent.getStringExtra(BLOCKED_TRACKERS_EXTRA).orEmpty()
        val upgradedHttps = intent.getBooleanExtra(UPGRADED_TO_HTTPS_EXTRA, false)
        val surrogates = intent.getStringExtra(SURROGATES_EXTRA).orEmpty()
        val urlParametersRemoved = intent.getBooleanExtra(URL_PARAMETERS_REMOVED_EXTRA, false)
        viewModel.setInitialBrokenSite(url, blockedTrackers, surrogates, upgradedHttps, urlParametersRemoved)
    }

    private fun configureListeners() {
        val categories = viewModel.categories.map { getString(it.category) }.toTypedArray()

        brokenSites.categoriesSelection.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.brokenSitesCategoriesTitle))
                .setSingleChoiceItems(categories, viewModel.indexSelected) { _, newIndex ->
                    viewModel.onCategoryIndexChanged(newIndex)
                }
                .setPositiveButton(getString(android.R.string.yes)) { dialog, _ ->
                    viewModel.onCategoryAccepted()
                    dialog.dismiss()
                }
                .setNegativeButton(getString(android.R.string.no)) { dialog, _ ->
                    viewModel.onCategorySelectionCancelled()
                    dialog.dismiss()
                }
                .show()
        }

        brokenSites.submitButton.setOnClickListener {
            val webViewVersion = webViewVersionProvider.getFullVersion()
            viewModel.onSubmitPressed(webViewVersion)
        }
    }

    private fun configureObservers() {
        viewModel.command.observe(
            this,
            Observer {
                it?.let { processCommand(it) }
            }
        )
        viewModel.viewState.observe(
            this,
            Observer {
                it?.let { render(it) }
            }
        )
    }

    private fun processCommand(command: Command) {
        when (command) {
            Command.ConfirmAndFinish -> confirmAndFinish()
        }
    }

    private fun confirmAndFinish() {
        Toast.makeText(this, R.string.brokenSiteSubmitted, Toast.LENGTH_LONG).show()
        finishAfterTransition()
    }

    private fun render(viewState: ViewState) {
        val category = viewState.categorySelected?.let {
            getString(viewState.categorySelected.category)
        }.orEmpty()
        brokenSites.categoriesSelection.setText(category)
        brokenSites.submitButton.isEnabled = viewState.submitAllowed
    }

    companion object {

        private const val URL_EXTRA = "URL_EXTRA"
        private const val BLOCKED_TRACKERS_EXTRA = "BLOCKED_TRACKERS_EXTRA"
        private const val UPGRADED_TO_HTTPS_EXTRA = "UPGRADED_TO_HTTPS_EXTRA"
        private const val SURROGATES_EXTRA = "SURROGATES_EXTRA"
        private const val URL_PARAMETERS_REMOVED_EXTRA = "URL_PARAMETERS_REMOVED_EXTRA"

        fun intent(
            context: Context,
            data: BrokenSiteData
        ): Intent {
            val intent = Intent(context, BrokenSiteActivity::class.java)
            intent.putExtra(URL_EXTRA, data.url)
            intent.putExtra(BLOCKED_TRACKERS_EXTRA, data.blockedTrackers)
            intent.putExtra(SURROGATES_EXTRA, data.surrogates)
            intent.putExtra(UPGRADED_TO_HTTPS_EXTRA, data.upgradedToHttps)
            intent.putExtra(URL_PARAMETERS_REMOVED_EXTRA, data.urlParametersRemoved)
            return intent
        }
    }
}
