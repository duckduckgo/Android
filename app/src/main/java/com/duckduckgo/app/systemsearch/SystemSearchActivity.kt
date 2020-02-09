/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.systemsearch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.LaunchApplication
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.LaunchBrowser
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.SystemSearchViewState
import kotlinx.android.synthetic.main.activity_system_search.*
import timber.log.Timber
import javax.inject.Inject

class SystemSearchActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: SystemSearchViewModel by bindViewModel()

    private lateinit var autocompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter
    private lateinit var deviceAppSuggestionsAdapter: DeviceAppSuggestionsAdapter

    private val textChangeWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            viewModel.userUpdatedQuery(omnibarTextInput.text.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_search)
        configureObservers()
        configureAutoComplete()
        configureDeviceAppSuggestions()
        configureTextInput()
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        Timber.i("onNewIntent: $newIntent")

        viewModel.resetState()
        val intent = newIntent ?: return
        when {
            launchedFromAssist(intent) -> pixel.fire(Pixel.PixelName.ASSIST_LAUNCH)
            launchedFromWidget(intent) -> pixel.fire(Pixel.PixelName.WIDGET_LAUNCH)
            launchedFromAppBar(intent) -> pixel.fire(Pixel.PixelName.GOOGLE_BAR_LAUNCH)
        }
    }

    private fun configureObservers() {
        viewModel.viewState.observe(this, Observer<SystemSearchViewState> {
            it?.let { renderViewState(it) }
        })
        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    private fun configureAutoComplete() {
        autocompleteSuggestions.layoutManager = LinearLayoutManager(this)
        autocompleteSuggestionsAdapter = BrowserAutoCompleteSuggestionsAdapter(
            immediateSearchClickListener = {
                viewModel.userSubmittedQuery(it.phrase)
            },
            editableSearchClickListener = {
                viewModel.userUpdatedQuery(it.phrase)
            },
            showsMessageOnNoSuggestions = false
        )
        autocompleteSuggestions.adapter = autocompleteSuggestionsAdapter
    }

    private fun configureDeviceAppSuggestions() {
        deviceAppSuggestions.layoutManager = LinearLayoutManager(this)
        deviceAppSuggestionsAdapter = DeviceAppSuggestionsAdapter {
            viewModel.userSelectedApp(it)
        }
        deviceAppSuggestions.adapter = deviceAppSuggestionsAdapter
    }

    private fun configureTextInput() {
        omnibarTextInput.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                viewModel.userSubmittedQuery(omnibarTextInput.text.toString())
                return@OnEditorActionListener true
            }
            false
        })

        omnibarTextInput.removeTextChangedListener(textChangeWatcher)
        omnibarTextInput.addTextChangedListener(textChangeWatcher)
        clearTextButton.setOnClickListener { viewModel.userClearedQuery() }
    }

    private fun renderViewState(viewState: SystemSearchViewState) {
        if (omnibarTextInput.text.toString() != viewState.queryText) {
            omnibarTextInput.setText(viewState.queryText)
            omnibarTextInput.setSelection(viewState.queryText.length)
        }
        autocompleteSuggestionsAdapter.updateData(viewState.autocompleteResults)
        deviceLabel.isVisible = viewState.appResults.isNotEmpty()
        deviceAppSuggestionsAdapter.updateData(viewState.appResults)
    }

    private fun processCommand(command: SystemSearchViewModel.Command) {
        when (command) {
            is LaunchBrowser -> {
                startActivity(BrowserActivity.intent(this, command.query))
                finish()

            }
            is LaunchApplication -> {
                startActivity(command.intent)
                finish()
            }
        }
    }

    private fun launchedFromAppBar(intent: Intent): Boolean {
        return intent.action == NEW_SEARCH_ACTION
    }

    private fun launchedFromAssist(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_ASSIST
    }

    private fun launchedFromWidget(intent: Intent): Boolean {
        return intent.getBooleanExtra(WIDGET_SEARCH_EXTRA, false)
    }

    companion object {

        const val WIDGET_SEARCH_EXTRA = "WIDGET_SEARCH_EXTRA"
        const val NEW_SEARCH_ACTION = "com.duckduckgo.mobile.android.NEW_SEARCH"

        fun intent(context: Context, widgetSearch: Boolean = false): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_EXTRA, widgetSearch)
            return intent
        }
    }
}