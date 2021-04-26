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
import com.duckduckgo.app.fire.DataClearerForegroundAppRestartPixel
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.global.view.hideKeyboard
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.*
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.SystemSearchResultsViewState
import kotlinx.android.synthetic.main.activity_system_search.*
import kotlinx.android.synthetic.main.activity_system_search.appBarLayout
import kotlinx.android.synthetic.main.activity_system_search.autocompleteSuggestions
import kotlinx.android.synthetic.main.activity_system_search.clearTextButton
import kotlinx.android.synthetic.main.activity_system_search.deviceAppSuggestions
import kotlinx.android.synthetic.main.activity_system_search.deviceLabel
import kotlinx.android.synthetic.main.activity_system_search.logo
import kotlinx.android.synthetic.main.activity_system_search.omnibarTextInput
import kotlinx.android.synthetic.main.activity_system_search.results
import kotlinx.android.synthetic.main.activity_system_search.resultsContent
import kotlinx.android.synthetic.main.include_system_search_onboarding.*
import javax.inject.Inject

class SystemSearchActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var omnibarScrolling: OmnibarScrolling

    @Inject
    lateinit var dataClearerForegroundAppRestartPixel: DataClearerForegroundAppRestartPixel

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
        dataClearerForegroundAppRestartPixel.registerIntent(intent)
        setContentView(R.layout.activity_system_search)
        configureObservers()
        configureOnboarding()
        configureAutoComplete()
        configureDeviceAppSuggestions()
        configureDaxButton()
        configureOmnibar()
        configureTextInput()

        if (savedInstanceState == null) {
            intent?.let { sendLaunchPixels(it) }
        }
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        dataClearerForegroundAppRestartPixel.registerIntent(newIntent)
        viewModel.resetViewState()
        newIntent?.let { sendLaunchPixels(it) }
    }

    private fun sendLaunchPixels(intent: Intent) {
        when {
            launchedFromAssist(intent) -> pixel.fire(AppPixelName.APP_ASSIST_LAUNCH)
            launchedFromWidget(intent) -> pixel.fire(AppPixelName.APP_WIDGET_LAUNCH)
            launchedFromNotification(intent) -> pixel.fire(AppPixelName.APP_NOTIFICATION_LAUNCH)
            launchedFromSystemSearchBox(intent) -> pixel.fire(AppPixelName.APP_SYSTEM_SEARCH_BOX_LAUNCH)
        }
    }

    private fun configureObservers() {
        viewModel.onboardingViewState.observe(
            this,
            Observer<SystemSearchViewModel.OnboardingViewState> {
                it?.let { renderOnboardingViewState(it) }
            }
        )
        viewModel.resultsViewState.observe(
            this,
            Observer<SystemSearchResultsViewState> {
                it?.let { renderResultsViewState(it) }
            }
        )
        viewModel.command.observe(
            this,
            Observer {
                processCommand(it)
            }
        )
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
                viewModel.onUserSelectedToEditQuery(it.phrase)
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
        resultsContent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateScroll() }
    }

    private fun updateScroll() {
        val scrollable = resultsContent.height > (results.height - results.paddingTop - results.paddingBottom)
        if (scrollable) {
            omnibarScrolling.enableOmnibarScrolling(toolbarContainer)
        } else {
            showOmnibar()
            omnibarScrolling.disableOmnibarScrolling(toolbarContainer)
        }
    }

    private fun configureTextInput() {
        omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    viewModel.userSubmittedQuery(omnibarTextInput.text.toString())
                    return@OnEditorActionListener true
                }
                false
            }
        )

        omnibarTextInput.removeTextChangedListener(textChangeWatcher)
        omnibarTextInput.addTextChangedListener(textChangeWatcher)
        clearTextButton.setOnClickListener { viewModel.userRequestedClear() }
    }

    private fun renderOnboardingViewState(viewState: SystemSearchViewModel.OnboardingViewState) {
        if (viewState.visible) {
            onboarding.visibility = View.VISIBLE
            results.elevation = 0.0f
            checkmarks.visibility = if (viewState.expanded) View.VISIBLE else View.GONE
            refreshOnboardingToggleText(viewState.expanded)
        } else {
            onboarding.visibility = View.GONE
            results.elevation = resources.getDimension(R.dimen.systemSearchResultsElevation)
        }
    }

    private fun refreshOnboardingToggleText(expanded: Boolean) {
        val toggleText = if (expanded) R.string.systemSearchOnboardingButtonLess else R.string.systemSearchOnboardingButtonMore
        toggleButton.text = getString(toggleText)
    }

    private fun renderResultsViewState(viewState: SystemSearchResultsViewState) {
        deviceLabel.isVisible = viewState.appResults.isNotEmpty()
        autocompleteSuggestionsAdapter.updateData(viewState.autocompleteResults.query, viewState.autocompleteResults.suggestions)
        deviceAppSuggestionsAdapter.updateData(viewState.appResults)
    }

    private fun processCommand(command: SystemSearchViewModel.Command) {
        when (command) {
            is ClearInputText -> {
                omnibarTextInput.removeTextChangedListener(textChangeWatcher)
                omnibarTextInput.setText("")
                omnibarTextInput.addTextChangedListener(textChangeWatcher)
            }
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
            is EditQuery -> {
                editQuery(command.query)
            }
        }
    }

    private fun editQuery(query: String) {
        omnibarTextInput.setText(query)
        omnibarTextInput.setSelection(query.length)
    }

    private fun launchDuckDuckGo() {
        startActivity(BrowserActivity.intent(this))
        finish()
    }

    private fun launchBrowser(command: LaunchBrowser) {
        startActivity(BrowserActivity.intent(this, command.query))
        finish()
    }

    private fun launchDeviceApp(command: LaunchDeviceApplication) {
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

    private fun launchedFromNotification(intent: Intent): Boolean {
        return intent.getBooleanExtra(NOTIFICATION_SEARCH_EXTRA, false)
    }

    companion object {

        const val NOTIFICATION_SEARCH_EXTRA = "NOTIFICATION_SEARCH_EXTRA"
        const val WIDGET_SEARCH_EXTRA = "WIDGET_SEARCH_EXTRA"
        const val NEW_SEARCH_ACTION = "com.duckduckgo.mobile.android.NEW_SEARCH"

        fun fromWidget(context: Context): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_EXTRA, true)
            intent.putExtra(NOTIFICATION_SEARCH_EXTRA, false)
            return intent
        }

        fun fromNotification(context: Context): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_EXTRA, false)
            intent.putExtra(NOTIFICATION_SEARCH_EXTRA, true)
            return intent
        }
    }
}
