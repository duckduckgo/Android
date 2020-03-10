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
import android.widget.EditText
import androidx.lifecycle.Observer
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.Command
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.ViewState
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.content_broken_sites.*
import kotlinx.android.synthetic.main.include_toolbar.*
import org.jetbrains.anko.longToast


class BrokenSiteActivity : DuckDuckGoActivity() {
    // TODO Change code here
    private val viewModel: BrokenSiteViewModel by bindViewModel()
    private var lol: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_broken_site)
        configureListeners()
        configureObservers()
        setupActionBar()
        submitButton.isEnabled = false
        if (savedInstanceState == null) {
            consumeIntentExtra()
        }

        val categories = resources.getStringArray(R.array.brokenSitesCategories)

        categoriesSelection.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.brokenSitesCategoriesTitle))
                .setSingleChoiceItems(categories, -1) { _, itemSelected ->
                    lol = itemSelected
                }
                .setPositiveButton(getString(android.R.string.yes)) { _, _ ->
                    when (lol) {
                        -1 -> {
                            categoriesSelection.setText("")
                            submitButton.isEnabled = false
                        }
                        else -> {
                            categoriesSelection.setText(categories[lol])
                            submitButton.isEnabled = true
                        }
                    }
                }
                .setNegativeButton(getString(android.R.string.no)) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }


    private fun setupActionBar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun consumeIntentExtra() {
        val url = intent.getStringExtra(URL_EXTRA)
        val blockedTrackers = intent.getStringExtra(BLOCKED_TRACKERS_EXTRA)
        viewModel.setInitialBrokenSite(url, blockedTrackers)
    }

    private fun configureListeners() {
//        feedbackMessage.addTextChangedListener(object : TextChangedWatcher() {
//            override fun afterTextChanged(editable: Editable) {
//                viewModel.onFeedbackMessageChanged(editable.toString())
//            }
//        })
//        brokenSiteUrl.addTextChangedListener(object : TextChangedWatcher() {
//            override fun afterTextChanged(editable: Editable) {
//                viewModel.onBrokenSiteUrlChanged(editable.toString())
//            }
//        })
//        submitButton.setOnClickListener {
//            val webViewVersion = WebViewCompat.getCurrentWebViewPackage(applicationContext)?.versionName ?: BrokenSiteViewModel.UNKNOWN_VERSION
//            viewModel.onSubmitPressed(webViewVersion)
//        }
    }

    private fun configureObservers() {
        viewModel.command.observe(this, Observer {
            it?.let { processCommand(it) }
        })
        viewModel.viewState.observe(this, Observer<ViewState> {
            it?.let { render(it) }
        })
    }

    private fun processCommand(command: Command) {
        when (command) {
//            Command.FocusUrl -> brokenSiteUrl.requestFocus()
//            Command.FocusMessage -> feedbackMessage.requestFocus()
            Command.ConfirmAndFinish -> confirmAndFinish()
        }
    }

    private fun confirmAndFinish() {
        longToast(R.string.brokenSiteSubmitted)
        finishAfterTransition()
    }

    private fun render(viewState: ViewState) {
//        brokenSiteUrl.updateText(viewState.url ?: "")
//        submitButton.isEnabled = viewState.submitAllowed
    }

    private fun EditText.updateText(newText: String) {
        if (text.toString() != newText) {
            setText(newText)
        }
    }

    companion object {

        private const val URL_EXTRA = "URL_EXTRA"
        private const val BLOCKED_TRACKERS_EXTRA = "BLOCKED_TRACKERS_EXTRA"

        fun intent(context: Context, url: String? = null, blockedTrackers: String?): Intent {
            val intent = Intent(context, BrokenSiteActivity::class.java)
            if (url != null) {
                intent.putExtra(URL_EXTRA, url)
            }
            if (blockedTrackers != null) {
                intent.putExtra(BLOCKED_TRACKERS_EXTRA, blockedTrackers)
            }
            return intent
        }

    }
}
