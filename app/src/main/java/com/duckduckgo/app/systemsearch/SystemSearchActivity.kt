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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.omnibar.OmnibarScrolling
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.global.view.hideKeyboard
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.*
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.SystemSearchResultsViewState
import kotlinx.android.synthetic.main.activity_system_search.*
import kotlinx.android.synthetic.main.include_system_search_onboarding.*
import kotlinx.android.synthetic.main.include_system_search_onboarding_content.*
import javax.inject.Inject

class SystemSearchActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var omnibarScrolling: OmnibarScrolling

    private val viewModel: SystemSearchViewModel by bindViewModel()
    private lateinit var autocompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter
    private lateinit var deviceAppSuggestionsAdapter: DeviceAppSuggestionsAdapter

    private val textChangeWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            showOmnibar()
            viewModel.userUpdatedQuery(omnibarTextInput.text.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_search)
        configureObservers()
        configureOnboarding()
        configureAutoComplete()
        configureDeviceAppSuggestions()
        configureDaxButton()
        configureOmnibar()
        configureTextInput()
        intent?.let { sendLaunchPixels(it) }
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        viewModel.resetViewState()
        newIntent?.let { sendLaunchPixels(it) }
    }

    private fun sendLaunchPixels(intent: Intent) {
        when {
            launchedFromAssist(intent) -> pixel.fire(PixelName.APP_ASSIST_LAUNCH)
            launchedFromWidget(intent) -> pixel.fire(PixelName.APP_WIDGET_LAUNCH)
            launchedFromSystemSearchBox(intent) -> pixel.fire(PixelName.APP_SYSTEM_SEARCH_BOX_LAUNCH)
        }
    }

    private fun configureObservers() {
        viewModel.onboardingViewState.observe(this, Observer<SystemSearchViewModel.OnboardingViewState> {
            it?.let { renderOnboardingViewState(it) }
        })
        viewModel.resutlsViewState.observe(this, Observer<SystemSearchResultsViewState> {
            it?.let { renderResultsViewState(it) }
        })
        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    private fun configureOnboarding() {
        okButton.setOnClickListener {
            viewModel.userDismissedOnboarding()
        }
        toggleButton.setOnClickListener {
            viewModel.userTappedOnboardingToggle()
        }
    }

    private fun configureAutoComplete() {
        autocompleteSuggestions.layoutManager = LinearLayoutManager(this)
        autocompleteSuggestionsAdapter = BrowserAutoCompleteSuggestionsAdapter(
            immediateSearchClickListener = {
                viewModel.userSubmittedAutocompleteResult(it.phrase)
            },
            editableSearchClickListener = {
                viewModel.userUpdatedQuery(it.phrase)
            }
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

    private fun configureDaxButton() {
        logo.setOnClickListener {
            viewModel.userTappedDax()
        }
    }

    private fun configureOmnibar() {
        results.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val scrollable = results.maxScrollAmount > MINIMUM_SCROLL_HEIGHT
            if (scrollable) {
                omnibarScrolling.enableOmnibarScrolling(toolbar)
            } else {
                showOmnibar()
                omnibarScrolling.disableOmnibarScrolling(toolbar)
            }
        }
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

    private fun renderOnboardingViewState(viewState: SystemSearchViewModel.OnboardingViewState) {
        onboarding.visibility = if (viewState.visibile) View.VISIBLE else View.GONE
        checkmarks.visibility = if (viewState.expanded) View.VISIBLE else View.GONE

        val toggleText = if (viewState.expanded) R.string.systemSearchOnboardingButtonLess else R.string.systemSearchOnboardingButtonMore
        toggleButton.text = getString(toggleText)
    }

    private fun renderResultsViewState(viewState: SystemSearchResultsViewState) {
        if (omnibarTextInput.text.toString() != viewState.queryText) {
            omnibarTextInput.setText(viewState.queryText)
            omnibarTextInput.setSelection(viewState.queryText.length)
        }

        deviceLabel.isVisible = viewState.appResults.isNotEmpty()
        autocompleteSuggestionsAdapter.updateData(viewState.autocompleteResults.query, viewState.autocompleteResults.suggestions)
        deviceAppSuggestionsAdapter.updateData(viewState.appResults)
    }

    private fun processCommand(command: SystemSearchViewModel.Command) {
        when (command) {
            is LaunchDuckDuckGo -> {
                launchDuckDuckGo()
            }
            is LaunchBrowser -> {
                launchBrowser(command)
            }
            is LaunchDeviceApplication -> {
                launchDeviceApp(command)
            }
            is ShowAppNotFoundMessage -> {
                Toast.makeText(this, R.string.systemSearchAppNotFound, LENGTH_SHORT).show()
            }
            is DismissKeyboard -> {
                omnibarTextInput.hideKeyboard()
            }
        }
    }

    private fun launchDuckDuckGo() {
        pixel.fire(PixelName.INTERSTITIAL_LAUNCH_DAX)
        startActivity(BrowserActivity.intent(this))
        finish()
    }

    private fun launchBrowser(command: LaunchBrowser) {
        pixel.fire(PixelName.INTERSTITIAL_LAUNCH_BROWSER_QUERY)
        startActivity(BrowserActivity.intent(this, command.query))
        finish()
    }

    private fun launchDeviceApp(command: LaunchDeviceApplication) {
        pixel.fire(PixelName.INTERSTITIAL_LAUNCH_DEVICE_APP)
        try {
            startActivity(command.deviceApp.launchIntent)
            finish()
        } catch (error: ActivityNotFoundException) {
            viewModel.appNotFound(command.deviceApp)
        }
    }

    private fun showOmnibar() {
        results.scrollTo(0, 0)
        appBarLayout.setExpanded(true)
    }

    private fun launchedFromSystemSearchBox(intent: Intent): Boolean {
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
        const val MINIMUM_SCROLL_HEIGHT = 86 // enough space for blank "no suggestion" and padding

        fun intent(context: Context, widgetSearch: Boolean = false): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_EXTRA, widgetSearch)
            return intent
        }
    }
}