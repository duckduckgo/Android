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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.databinding.ActivitySystemSearchBinding
import com.duckduckgo.app.browser.databinding.IncludeQuickAccessItemsBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.Companion.QUICK_ACCESS_ITEM_MAX_SIZE_DP
import com.duckduckgo.app.browser.favorites.QuickAccessDragTouchItemListener
import com.duckduckgo.app.browser.omnibar.OmnibarScrolling
import com.duckduckgo.app.fire.DataClearerForegroundAppRestartPixel
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.global.view.html
import com.duckduckgo.mobile.android.ui.view.*
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.*
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject

class SystemSearchActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var omnibarScrolling: OmnibarScrolling

    @Inject
    lateinit var dataClearerForegroundAppRestartPixel: DataClearerForegroundAppRestartPixel

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    private val viewModel: SystemSearchViewModel by bindViewModel()
    private val binding: ActivitySystemSearchBinding by viewBinding()
    private lateinit var quickAccessItemsBinding: IncludeQuickAccessItemsBinding
    private lateinit var autocompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter
    private lateinit var deviceAppSuggestionsAdapter: DeviceAppSuggestionsAdapter
    private lateinit var quickAccessAdapter: FavoritesQuickAccessAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val systemSearchOnboarding
        get() = binding.includeSystemSearchOnboarding

    private val omnibarTextInput
        get() = binding.omnibarTextInput

    private val textChangeWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            showOmnibar()
            viewModel.userUpdatedQuery(omnibarTextInput.text.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataClearerForegroundAppRestartPixel.registerIntent(intent)
        quickAccessItemsBinding = IncludeQuickAccessItemsBinding.bind(binding.root)
        setContentView(binding.root)
        configureObservers()
        configureOnboarding()
        configureAutoComplete()
        configureDeviceAppSuggestions()
        configureDaxButton()
        configureOmnibar()
        configureTextInput()
        configureQuickAccessGrid()

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
            launchedFromSearchWithFavsWidget(intent) -> pixel.fire(AppPixelName.APP_FAVORITES_SEARCHBAR_WIDGET_LAUNCH)
            launchedFromNotification(intent) -> pixel.fire(AppPixelName.APP_NOTIFICATION_LAUNCH)
            launchedFromSystemSearchBox(intent) -> pixel.fire(AppPixelName.APP_SYSTEM_SEARCH_BOX_LAUNCH)
        }
    }

    private fun configureObservers() {
        viewModel.onboardingViewState.observe(
            this,
            {
                it?.let { renderOnboardingViewState(it) }
            }
        )
        viewModel.resultsViewState.observe(
            this,
            {
                when (it) {
                    is SystemSearchViewModel.Suggestions.SystemSearchResultsViewState -> {
                        renderResultsViewState(it)
                    }
                    is SystemSearchViewModel.Suggestions.QuickAccessItems -> {
                        renderQuickAccessItems(it)
                    }
                }
            }
        )
        viewModel.command.observe(
            this,
            {
                processCommand(it)
            }
        )
    }

    private fun configureOnboarding() {
        systemSearchOnboarding.okButton.setOnClickListener {
            viewModel.userDismissedOnboarding()
        }
        systemSearchOnboarding.toggleButton.setOnClickListener {
            viewModel.userTappedOnboardingToggle()
        }
    }

    private fun configureAutoComplete() {
        binding.autocompleteSuggestions.layoutManager = LinearLayoutManager(this)
        autocompleteSuggestionsAdapter = BrowserAutoCompleteSuggestionsAdapter(
            immediateSearchClickListener = {
                viewModel.userSubmittedAutocompleteResult(it.phrase)
            },
            editableSearchClickListener = {
                viewModel.onUserSelectedToEditQuery(it.phrase)
            }
        )
        binding.autocompleteSuggestions.adapter = autocompleteSuggestionsAdapter
    }

    private fun configureDeviceAppSuggestions() {
        binding.deviceAppSuggestions.layoutManager = LinearLayoutManager(this)
        deviceAppSuggestionsAdapter = DeviceAppSuggestionsAdapter {
            viewModel.userSelectedApp(it)
        }
        binding.deviceAppSuggestions.adapter = deviceAppSuggestionsAdapter
    }

    private fun configureQuickAccessGrid() {
        val quickAccessRecyclerView = quickAccessItemsBinding.quickAccessRecyclerView
        val numOfColumns = gridViewColumnCalculator.calculateNumberOfColumns(QUICK_ACCESS_ITEM_MAX_SIZE_DP, QUICK_ACCESS_GRID_MAX_COLUMNS)
        val layoutManager = GridLayoutManager(this, numOfColumns)
        quickAccessRecyclerView.layoutManager = layoutManager
        quickAccessAdapter = FavoritesQuickAccessAdapter(
            this, faviconManager,
            { viewHolder -> itemTouchHelper.startDrag(viewHolder) },
            { viewModel.onQuickAccesItemClicked(it) },
            { viewModel.onEditQuickAccessItemRequested(it) },
            { viewModel.onDeleteQuickAccessItemRequested(it) }
        )
        itemTouchHelper = ItemTouchHelper(
            QuickAccessDragTouchItemListener(
                quickAccessAdapter,
                object : QuickAccessDragTouchItemListener.DragDropListener {
                    override fun onListChanged(listElements: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) {
                        viewModel.onQuickAccessListChanged(listElements)
                    }
                }
            )
        )

        itemTouchHelper.attachToRecyclerView(quickAccessRecyclerView)
        quickAccessRecyclerView.adapter = quickAccessAdapter
        val sidePadding = gridViewColumnCalculator.calculateSidePadding(QUICK_ACCESS_ITEM_MAX_SIZE_DP, numOfColumns)
        quickAccessRecyclerView.setPadding(sidePadding, 8.toPx(), sidePadding, 8.toPx())
    }

    private fun configureDaxButton() {
        binding.logo.setOnClickListener {
            viewModel.userTappedDax()
        }
    }

    private fun configureOmnibar() {
        binding.resultsContent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateScroll() }
    }

    private fun showEditSavedSiteDialog(savedSite: SavedSite) {
        val dialog = EditSavedSiteDialogFragment.instance(savedSite)
        dialog.show(supportFragmentManager, "EDIT_BOOKMARK")
        dialog.listener = viewModel
    }

    private fun updateScroll() {
        val results = binding.results
        val scrollable = binding.resultsContent.height > (results.height - results.paddingTop - results.paddingBottom)
        if (scrollable) {
            omnibarScrolling.enableOmnibarScrolling(binding.toolbarContainer)
        } else {
            showOmnibar()
            omnibarScrolling.disableOmnibarScrolling(binding.toolbarContainer)
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
        binding.clearTextButton.setOnClickListener { viewModel.userRequestedClear() }
    }

    private fun renderOnboardingViewState(viewState: SystemSearchViewModel.OnboardingViewState) {
        if (viewState.visible) {
            systemSearchOnboarding.onboarding.visibility = View.VISIBLE
            binding.results.elevation = 0.0f
            systemSearchOnboarding.checkmarks.visibility = if (viewState.expanded) View.VISIBLE else View.GONE
            refreshOnboardingToggleText(viewState.expanded)
        } else {
            systemSearchOnboarding.onboarding.visibility = View.GONE
            binding.results.elevation = resources.getDimension(R.dimen.systemSearchResultsElevation)
        }
    }

    private fun refreshOnboardingToggleText(expanded: Boolean) {
        val toggleText = if (expanded) R.string.systemSearchOnboardingButtonLess else R.string.systemSearchOnboardingButtonMore
        systemSearchOnboarding.toggleButton.text = getString(toggleText)
    }

    private fun renderResultsViewState(viewState: SystemSearchViewModel.Suggestions.SystemSearchResultsViewState) {
        binding.deviceLabel.isVisible = viewState.appResults.isNotEmpty()
        autocompleteSuggestionsAdapter.updateData(viewState.autocompleteResults.query, viewState.autocompleteResults.suggestions)
        deviceAppSuggestionsAdapter.updateData(viewState.appResults)
    }

    private fun renderQuickAccessItems(it: SystemSearchViewModel.Suggestions.QuickAccessItems) {
        quickAccessAdapter.submitList(it.favorites)
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
            is LaunchEditDialog -> {
                showEditSavedSiteDialog(command.savedSite)
            }
            is DeleteSavedSiteConfirmation -> {
                confirmDeleteSavedSite(command.savedSite)
            }
        }
    }

    private fun editQuery(query: String) {
        omnibarTextInput.setText(query)
        omnibarTextInput.setSelection(query.length)
    }

    private fun confirmDeleteSavedSite(savedSite: SavedSite) {
        val message = getString(R.string.bookmarkDeleteConfirmationMessage, savedSite.title).html(this)
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.insertQuickAccessItem(savedSite)
        }.show()
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
        binding.results.scrollTo(0, 0)
        binding.appBarLayout.setExpanded(true)
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

    private fun launchedFromSearchWithFavsWidget(intent: Intent): Boolean {
        return intent.getBooleanExtra(WIDGET_SEARCH_WITH_FAVS_EXTRA, false)
    }

    private fun launchedFromNotification(intent: Intent): Boolean {
        return intent.getBooleanExtra(NOTIFICATION_SEARCH_EXTRA, false)
    }

    companion object {
        const val NOTIFICATION_SEARCH_EXTRA = "NOTIFICATION_SEARCH_EXTRA"
        const val WIDGET_SEARCH_EXTRA = "WIDGET_SEARCH_EXTRA"
        const val WIDGET_SEARCH_WITH_FAVS_EXTRA = "WIDGET_SEARCH_WITH_FAVS_EXTRA"
        const val NEW_SEARCH_ACTION = "com.duckduckgo.mobile.android.NEW_SEARCH"
        private const val QUICK_ACCESS_GRID_MAX_COLUMNS = 6

        fun fromWidget(context: Context): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_EXTRA, true)
            intent.putExtra(NOTIFICATION_SEARCH_EXTRA, false)
            return intent
        }

        fun fromFavWidget(context: Context): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_WITH_FAVS_EXTRA, true)
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
