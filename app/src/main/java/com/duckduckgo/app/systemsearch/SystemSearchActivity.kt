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
import android.text.Spanned
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.content.ContextCompat
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.R.string
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.autocomplete.SuggestionItemDecoration
import com.duckduckgo.app.browser.databinding.ActivitySystemSearchBinding
import com.duckduckgo.app.browser.databinding.IncludeQuickAccessItemsBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter.Companion.QUICK_ACCESS_ITEM_MAX_SIZE_DP
import com.duckduckgo.app.browser.newtab.QuickAccessDragTouchItemListener
import com.duckduckgo.app.browser.omnibar.OmnibarScrolling
import com.duckduckgo.app.fire.DataClearerForegroundAppRestartPixel
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.*
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.ui.BrowserScreens.PrivateSearchScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.KeyboardVisibilityUtil
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchLauncher
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.WIDGET
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import logcat.LogPriority.VERBOSE
import logcat.logcat

@InjectWith(ActivityScope::class)
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

    @Inject
    lateinit var voiceSearchLauncher: VoiceSearchLauncher

    @Inject
    lateinit var voiceSearchAvailability: VoiceSearchAvailability

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val viewModel: SystemSearchViewModel by bindViewModel()
    private val binding: ActivitySystemSearchBinding by viewBinding()
    private lateinit var quickAccessItemsBinding: IncludeQuickAccessItemsBinding
    private lateinit var autocompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter
    private lateinit var deviceAppSuggestionsAdapter: DeviceAppSuggestionsAdapter
    private lateinit var quickAccessAdapter: FavoritesQuickAccessAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private var nestedScrollViewPosition: Int = 0
    private var nestedScrollViewRestorePosition: Int = 0

    private val systemSearchOnboarding
        get() = binding.includeSystemSearchOnboarding

    private val omnibarTextInput
        get() = binding.omnibarTextInput

    private val voiceSearch
        get() = binding.voiceSearchButton

    private val textChangeWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            showOmnibar()
            updateVoiceSearchVisibility()
            val searchQuery = omnibarTextInput.text.toString()
            binding.clearTextButton.isVisible = searchQuery.isNotEmpty()
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
        configureVoiceSearch()

        if (savedInstanceState == null) {
            intent?.let {
                sendLaunchPixels(it)
                handleVoiceSearchLaunch(it)
            }
        }
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        dataClearerForegroundAppRestartPixel.registerIntent(newIntent)
        viewModel.resetViewState()
        newIntent?.let {
            sendLaunchPixels(it)
            handleVoiceSearchLaunch(it)
        }
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

    private fun handleVoiceSearchLaunch(intent: Intent) {
        if (launchVoice(intent)) {
            voiceSearchLauncher.launch(this)
        }
    }

    private fun configureObservers() {
        viewModel.onboardingViewState.observe(
            this,
            {
                it?.let { renderOnboardingViewState(it) }
            },
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
            },
        )
        viewModel.command.observe(
            this,
            {
                processCommand(it)
            },
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
                viewModel.userSubmittedAutocompleteResult(it)
            },
            editableSearchClickListener = {
                viewModel.onUserSelectedToEditQuery(it.phrase)
            },
            autoCompleteInAppMessageDismissedListener = { viewModel.onUserDismissedAutoCompleteInAppMessage() },
            autoCompleteOpenSettingsClickListener = {
                globalActivityStarter.start(this, PrivateSearchScreenNoParams)
            },
            autoCompleteLongPressClickListener = {
                viewModel.userLongPressedAutocomplete(it)
            },
            omnibarPosition = settingsDataStore.omnibarPosition,
        )
        binding.autocompleteSuggestions.adapter = autocompleteSuggestionsAdapter
        binding.autocompleteSuggestions.addItemDecoration(
            SuggestionItemDecoration(ContextCompat.getDrawable(this, R.drawable.suggestions_divider)!!),
        )

        binding.results.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                nestedScrollViewPosition = scrollY
            },
        )
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
            this,
            faviconManager,
            { viewHolder -> itemTouchHelper.startDrag(viewHolder) },
            { viewModel.onQuickAccessItemClicked(it) },
            { viewModel.onEditQuickAccessItemRequested(it) },
            { viewModel.onDeleteQuickAccessItemRequested(it) },
            { viewModel.onDeleteSavedSiteRequested(it) },
        )
        itemTouchHelper = ItemTouchHelper(
            QuickAccessDragTouchItemListener(
                quickAccessAdapter,
                object : QuickAccessDragTouchItemListener.DragDropListener {
                    override fun onListChanged(listElements: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) {
                        viewModel.onQuickAccessListChanged(listElements)
                    }
                },
            ),
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

    private fun configureVoiceSearch() {
        if (voiceSearchAvailability.isVoiceSearchAvailable) {
            voiceSearch.visibility = View.VISIBLE
            voiceSearchLauncher.registerResultsCallback(this, this, WIDGET) {
                if (it is VoiceSearchLauncher.Event.VoiceRecognitionSuccess) {
                    viewModel.onUserSelectedToEditQuery(it.result)
                } else if (it is VoiceSearchLauncher.Event.VoiceSearchDisabled) {
                    viewModel.voiceSearchDisabled()
                }
            }
            voiceSearch.setOnClickListener {
                omnibarTextInput.hideKeyboard()
                voiceSearchLauncher.launch(this)
            }
        } else {
            voiceSearch.visibility = View.GONE
        }
        binding.spacer.isVisible = voiceSearch.isVisible && binding.clearTextButton.isVisible
    }

    private fun updateVoiceSearchVisibility() {
        val searchQuery = omnibarTextInput.text.toString()
        voiceSearch.isVisible =
            voiceSearchAvailability.shouldShowVoiceSearch(true, omnibarTextInput.text.toString(), omnibarTextInput.text.toString().isNotEmpty(), "")
        binding.clearTextButton.isVisible = searchQuery.isNotEmpty()
        binding.spacer.isVisible = voiceSearch.isVisible && binding.clearTextButton.isVisible
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
                if ((keyEvent == null && actionId == EditorInfo.IME_ACTION_GO) ||
                    (keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER && keyEvent.action == KeyEvent.ACTION_DOWN)
                ) {
                    viewModel.userSubmittedQuery(omnibarTextInput.text.toString())
                    return@OnEditorActionListener true
                }
                false
            },
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
            binding.results.elevation = resources.getDimension(CommonR.dimen.keyline_1)
        }
    }

    private fun refreshOnboardingToggleText(expanded: Boolean) {
        val toggleText = if (expanded) R.string.systemSearchOnboardingButtonLess else R.string.systemSearchOnboardingButtonMore
        systemSearchOnboarding.toggleButton.text = getString(toggleText)
    }

    private fun renderResultsViewState(viewState: SystemSearchViewModel.Suggestions.SystemSearchResultsViewState) {
        binding.deviceLabel.isVisible = viewState.appResults.isNotEmpty()
        autocompleteSuggestionsAdapter.updateData(viewState.autocompleteResults.query, viewState.autocompleteResults.suggestions)
        if (viewState.autocompleteResults.suggestions.isEmpty()) {
            viewModel.autoCompleteSuggestionsGone()
        }
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
                updateVoiceSearchVisibility()
            }

            is LaunchDuckDuckGo -> {
                launchDuckDuckGo()
            }

            is LaunchBrowser -> {
                launchBrowser(command.query)
            }

            is LaunchBrowserAndSwitchToTab -> {
                launchBrowser(command.query, command.tabId)
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

            is DeleteFavoriteConfirmation -> {
                confirmDeleteFavorite(command.savedSite)
            }

            is DeleteSavedSiteConfirmation -> {
                confirmDeleteSavedSite(command.savedSite)
            }

            is UpdateVoiceSearch -> {
                updateVoiceSearchVisibility()
            }

            is ShowRemoveSearchSuggestionDialog -> {
                showRemoveSearchSuggestionDialog(command.suggestion)
            }

            AutocompleteItemRemoved -> autocompleteItemRemoved()
        }
    }

    private fun showRemoveSearchSuggestionDialog(suggestion: AutoCompleteSuggestion) {
        storeAutocompletePosition()
        hideKeyboardDelayed()

        TextAlertDialogBuilder(this)
            .setTitle(R.string.autocompleteRemoveItemTitle)
            .setCancellable(true)
            .setPositiveButton(R.string.autocompleteRemoveItemRemove)
            .setNegativeButton(R.string.autocompleteRemoveItemCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onRemoveSearchSuggestionConfirmed(suggestion, omnibarTextInput.text.toString())
                    }

                    override fun onNegativeButtonClicked() {
                        showKeyboardAndRestorePosition()
                    }

                    override fun onDialogCancelled() {
                        showKeyboardAndRestorePosition()
                    }
                },
            )
            .show()
    }

    private fun storeAutocompletePosition() {
        nestedScrollViewRestorePosition = nestedScrollViewPosition
    }

    private fun autocompleteItemRemoved() {
        showKeyboardAndRestorePosition()
    }

    private fun showKeyboardAndRestorePosition() {
        val rootView = omnibarTextInput.rootView
        val keyboardVisibilityUtil = KeyboardVisibilityUtil(rootView)
        keyboardVisibilityUtil.addKeyboardVisibilityListener {
            binding.results.scrollTo(0, nestedScrollViewRestorePosition)
        }
        showKeyboardDelayed()
    }

    private fun showKeyboardDelayed() {
        logcat(VERBOSE) { "Keyboard now showing" }
        omnibarTextInput.postDelayed(KEYBOARD_DELAY) { omnibarTextInput.showKeyboard() }
    }

    private fun hideKeyboardDelayed() {
        logcat(VERBOSE) { "Keyboard now hiding" }
        omnibarTextInput.postDelayed(KEYBOARD_DELAY) { omnibarTextInput.hideKeyboard() }
    }

    private fun editQuery(query: String) {
        omnibarTextInput.setText(query)
        omnibarTextInput.setSelection(query.length)
    }

    private fun confirmDeleteFavorite(savedSite: SavedSite) {
        confirmDelete(savedSite, getString(string.favoriteDeleteConfirmationMessage).toSpannable()) {
            viewModel.deleteFavoriteSnackbarDismissed(it)
        }
    }

    private fun confirmDeleteSavedSite(savedSite: SavedSite) {
        confirmDelete(savedSite, getString(com.duckduckgo.saved.sites.impl.R.string.bookmarkDeleteConfirmationMessage, savedSite.title).html(this)) {
            viewModel.deleteSavedSiteSnackbarDismissed(it)
        }
    }

    private fun confirmDelete(
        savedSite: SavedSite,
        message: Spanned,
        onDeleteSnackbarDismissed: (SavedSite) -> Unit,
    ) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.undoDelete(savedSite)
        }
            .addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        if (event != DISMISS_EVENT_ACTION) {
                            onDeleteSnackbarDismissed(savedSite)
                        }
                    }
                },
            )
            .show()
    }

    private fun launchDuckDuckGo() {
        startActivity(BrowserActivity.intent(this, interstitialScreen = true))
        finish()
    }

    private fun launchBrowser(query: String, openExistingTabId: String? = null) {
        startActivity(
            BrowserActivity.intent(
                context = this,
                queryExtra = query,
                interstitialScreen = true,
                openExistingTabId = openExistingTabId,
            ),
        )
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

    private fun launchVoice(intent: Intent): Boolean {
        return intent.getBooleanExtra(WIDGET_SEARCH_LAUNCH_VOICE, false)
    }

    companion object {
        const val NOTIFICATION_SEARCH_EXTRA = "NOTIFICATION_SEARCH_EXTRA"
        const val WIDGET_SEARCH_EXTRA = "WIDGET_SEARCH_EXTRA"
        const val WIDGET_SEARCH_WITH_FAVS_EXTRA = "WIDGET_SEARCH_WITH_FAVS_EXTRA"
        const val WIDGET_SEARCH_LAUNCH_VOICE = "WIDGET_SEARCH_LAUNCH_VOICE"
        const val NEW_SEARCH_ACTION = "com.duckduckgo.mobile.android.NEW_SEARCH"
        private const val QUICK_ACCESS_GRID_MAX_COLUMNS = 6
        private const val KEYBOARD_DELAY = 200L

        fun fromWidget(
            context: Context,
            launchVoice: Boolean = false,
        ): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_EXTRA, true)
            intent.putExtra(NOTIFICATION_SEARCH_EXTRA, false)
            intent.putExtra(WIDGET_SEARCH_LAUNCH_VOICE, launchVoice)
            return intent
        }

        fun fromFavWidget(
            context: Context,
            launchVoice: Boolean = false,
        ): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_WITH_FAVS_EXTRA, true)
            intent.putExtra(NOTIFICATION_SEARCH_EXTRA, false)
            intent.putExtra(WIDGET_SEARCH_LAUNCH_VOICE, launchVoice)
            return intent
        }

        fun fromNotification(context: Context): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_EXTRA, false)
            intent.putExtra(NOTIFICATION_SEARCH_EXTRA, true)
            intent.putExtra(WIDGET_SEARCH_LAUNCH_VOICE, false)
            return intent
        }
    }
}
