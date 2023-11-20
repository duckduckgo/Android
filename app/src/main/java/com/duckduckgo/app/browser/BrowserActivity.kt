/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_TEXT
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity.Companion.SAVED_SITE_URL_EXTRA
import com.duckduckgo.app.bookmarks.ui.BookmarksScreenNoParams
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.BrowserViewModel.Command.Query
import com.duckduckgo.app.browser.databinding.ActivityBrowserBinding
import com.duckduckgo.app.browser.databinding.IncludeOmnibarToolbarMockupBinding
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.downloads.DownloadsActivity
import com.duckduckgo.app.feedback.ui.common.FeedbackActivity
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.fire.DataClearerForegroundAppRestartPixel
import com.duckduckgo.app.global.*
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.global.view.renderIfChanged
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPage
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_CANCEL
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_PROMOTED_CANCEL
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.sitepermissions.SitePermissionsActivity
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.autofill.api.emailprotection.EmailProtectionLinkVerifier
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreen.PrivacyDashboardHybridWithTabIdParam
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

// open class so that we can test BrowserApplicationStateInfo
@InjectWith(ActivityScope::class)
open class BrowserActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var clearPersonalDataAction: ClearDataAction

    @Inject
    lateinit var dataClearer: DataClearer

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var playStoreUtils: PlayStoreUtils

    @Inject
    lateinit var dataClearerForegroundAppRestartPixel: DataClearerForegroundAppRestartPixel

    @Inject
    lateinit var ctaViewModel: CtaViewModel

    @Inject
    lateinit var variantManager: VariantManager

    @Inject
    lateinit var userEventsStore: UserEventsStore

    @Inject
    lateinit var serviceWorkerClientCompat: ServiceWorkerClientCompat

    @Inject
    lateinit var emailProtectionLinkVerifier: EmailProtectionLinkVerifier

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    private val lastActiveTabs = TabList()

    private var currentTab: BrowserTabFragment? = null

    private val viewModel: BrowserViewModel by bindViewModel()

    private var instanceStateBundles: CombinedInstanceState? = null

    private var lastIntent: Intent? = null

    private lateinit var renderer: BrowserStateRenderer

    private val binding: ActivityBrowserBinding by viewBinding()

    private lateinit var toolbarMockupBinding: IncludeOmnibarToolbarMockupBinding

    private var openMessageInNewTabJob: Job? = null

    @VisibleForTesting
    var destroyedByBackPress: Boolean = false

    private val startBookmarksActivityForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(SAVED_SITE_URL_EXTRA)?.let {
                    viewModel.onBookmarksActivityResult(it)
                }
            }
        }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.daggerInject()
        intent?.sanitize()
        Timber.i("onCreate called. freshAppLaunch: ${dataClearer.isFreshAppLaunch}, savedInstanceState: $savedInstanceState")
        dataClearerForegroundAppRestartPixel.registerIntent(intent)
        renderer = BrowserStateRenderer()
        val newInstanceState = if (dataClearer.isFreshAppLaunch) null else savedInstanceState
        instanceStateBundles = CombinedInstanceState(originalInstanceState = savedInstanceState, newInstanceState = newInstanceState)

        super.onCreate(savedInstanceState = newInstanceState, daggerInject = false)
        toolbarMockupBinding = IncludeOmnibarToolbarMockupBinding.bind(binding.root)
        setContentView(binding.root)
        viewModel.viewState.observe(this) {
            renderer.renderBrowserViewState(it)
        }
        viewModel.awaitClearDataFinishedNotification()
        initializeServiceWorker()

        intent?.getStringExtra(LAUNCH_FROM_NOTIFICATION_PIXEL_NAME)?.let {
            viewModel.onLaunchedFromNotification(it)
        }
        configureOnBackPressedListener()
    }

    override fun onStop() {
        openMessageInNewTabJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        currentTab = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Timber.i("onNewIntent: $intent")

        intent?.sanitize()

        dataClearerForegroundAppRestartPixel.registerIntent(intent)

        if (dataClearer.dataClearerState.value == ApplicationClearDataState.FINISHED) {
            Timber.i("Automatic data clearer has finished, so processing intent now")
            launchNewSearchOrQuery(intent)
        } else {
            Timber.i("Automatic data clearer not yet finished, so deferring processing of intent")
            lastIntent = intent
        }

        viewModel.launchFromThirdParty()
    }

    private fun initializeServiceWorker() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            try {
                ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(serviceWorkerClientCompat)
            } catch (e: Throwable) {
                Timber.w(e.localizedMessage)
            }
        }
    }

    private fun openNewTab(
        tabId: String,
        url: String? = null,
        skipHome: Boolean,
    ): BrowserTabFragment {
        Timber.i("Opening new tab, url: $url, tabId: $tabId")
        val fragment = BrowserTabFragment.newInstance(tabId, url, skipHome)
        addOrReplaceNewTab(fragment, tabId)
        currentTab = fragment
        return fragment
    }

    private fun openFavoritesOnboardingNewTab(tabId: String): BrowserTabFragment {
        pixel.fire(AppPixelName.APP_EMPTY_VIEW_WIDGET_LAUNCH)
        val fragment = BrowserTabFragment.newInstanceFavoritesOnboarding(tabId)
        addOrReplaceNewTab(fragment, tabId)
        currentTab = fragment
        return fragment
    }

    private fun addOrReplaceNewTab(
        fragment: BrowserTabFragment,
        tabId: String,
    ) {
        val transaction = supportFragmentManager.beginTransaction()
        val tab = currentTab
        if (tab == null) {
            transaction.replace(R.id.fragmentContainer, fragment, tabId)
        } else {
            transaction.hide(tab)
            transaction.add(R.id.fragmentContainer, fragment, tabId)
        }
        transaction.commit()
    }

    private fun selectTab(tab: TabEntity?) {
        Timber.v("Select tab: $tab")

        if (tab == null) return

        if (tab.tabId == currentTab?.tabId) return

        lastActiveTabs.add(tab.tabId)

        val fragment = supportFragmentManager.findFragmentByTag(tab.tabId) as? BrowserTabFragment
        if (fragment == null) {
            openNewTab(tab.tabId, tab.url, tab.skipHome)
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        currentTab?.let {
            transaction.hide(it)
        }
        transaction.show(fragment)
        transaction.commit()
        currentTab = fragment
    }

    private fun removeTabs(fragments: List<BrowserTabFragment>) {
        val transaction = supportFragmentManager.beginTransaction()
        fragments.forEach {
            transaction.remove(it)
            lastActiveTabs.remove(it.tabId)
        }
        transaction.commit()
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            currentTab?.onLongPressBackButton()
            true
        } else {
            super.onKeyLongPress(keyCode, event)
        }
    }

    private fun launchNewSearchOrQuery(intent: Intent?) {
        Timber.i("launchNewSearchOrQuery: $intent")

        if (intent == null) {
            return
        }

        if (intent.getBooleanExtra(LAUNCH_FROM_DEFAULT_BROWSER_DIALOG, false)) {
            setResult(DefaultBrowserPage.DEFAULT_BROWSER_RESULT_CODE_DIALOG_INTERNAL)
            finish()
            return
        }

        if (intent.getBooleanExtra(PERFORM_FIRE_ON_ENTRY_EXTRA, false)) {
            Timber.i("Clearing everything as a result of $PERFORM_FIRE_ON_ENTRY_EXTRA flag being set")
            appCoroutineScope.launch(dispatcherProvider.io()) {
                clearPersonalDataAction.clearTabsAndAllDataAsync(appInForeground = true, shouldFireDataClearPixel = true)
                clearPersonalDataAction.setAppUsedSinceLastClearFlag(false)
                clearPersonalDataAction.killAndRestartProcess(notifyDataCleared = false)
            }

            return
        }

        if (intent.getBooleanExtra(NOTIFY_DATA_CLEARED_EXTRA, false)) {
            Timber.i("Should notify data cleared")
            Toast.makeText(applicationContext, R.string.fireDataCleared, Toast.LENGTH_LONG).show()
        }

        if (intent.getBooleanExtra(FAVORITES_ONBOARDING_EXTRA, false)) {
            lifecycleScope.launch {
                val tabId = viewModel.onNewTabRequested()
                openFavoritesOnboardingNewTab(tabId)
            }
            return
        }

        if (emailProtectionLinkVerifier.shouldDelegateToInContextView(intent.dataString, currentTab?.inContextEmailProtectionShowing)) {
            currentTab?.showEmailProtectionInContextWebFlow(intent.dataString)
            Timber.v("Verification link was consumed, so don't allow it to open in a new tab")
            return
        }

        // the BrowserActivity will automatically clear its stack of activities when being brought to the foreground, so this can no longer be true
        currentTab?.inContextEmailProtectionShowing = false

        if (launchNewSearch(intent)) {
            Timber.w("new tab requested")
            lifecycleScope.launch { viewModel.onNewTabRequested() }
            return
        }

        val sharedText = intent.intentText
        if (sharedText != null) {
            if (intent.getBooleanExtra(ShortcutBuilder.SHORTCUT_EXTRA_ARG, false)) {
                Timber.d("Shortcut opened with url $sharedText")
                lifecycleScope.launch { viewModel.onOpenShortcut(sharedText) }
            } else if (intent.getBooleanExtra(LAUNCH_FROM_FAVORITES_WIDGET, false)) {
                Timber.d("Favorite clicked from widget $sharedText")
                lifecycleScope.launch { viewModel.onOpenFavoriteFromWidget(query = sharedText) }
                return
            } else {
                Timber.w("opening in new tab requested for $sharedText")
                lifecycleScope.launch { viewModel.onOpenInNewTabRequested(query = sharedText, skipHome = true) }
                return
            }
        }
    }

    private fun configureObservers() {
        viewModel.command.observe(this) {
            processCommand(it)
        }
        viewModel.selectedTab.observe(this) {
            if (it != null) {
                selectTab(it)
            }
        }
        viewModel.tabs.observe(this) {
            clearStaleTabs(it)
            removeOldTabs()
            lifecycleScope.launch { viewModel.onTabsUpdated(it) }
        }
    }

    private fun removeObservers() {
        viewModel.command.removeObservers(this)
        viewModel.selectedTab.removeObservers(this)
        viewModel.tabs.removeObservers(this)
    }

    private fun clearStaleTabs(updatedTabs: List<TabEntity>?) {
        if (updatedTabs == null) {
            return
        }

        val stale = supportFragmentManager
            .fragments.mapNotNull { it as? BrowserTabFragment }
            .filter { fragment -> updatedTabs.none { it.tabId == fragment.tabId } }

        if (stale.isNotEmpty()) {
            removeTabs(stale)
        }
    }

    private fun removeOldTabs() {
        val candidatesToRemove = lastActiveTabs.dropLast(MAX_ACTIVE_TABS)
        if (candidatesToRemove.isEmpty()) return

        val tabsToRemove = supportFragmentManager.fragments
            .mapNotNull { it as? BrowserTabFragment }
            .filter { candidatesToRemove.contains(it.tabId) }

        if (tabsToRemove.isNotEmpty()) {
            removeTabs(tabsToRemove)
        }
    }

    private fun processCommand(command: Command) {
        Timber.i("Processing command: $command")
        when (command) {
            is Query -> currentTab?.submitQuery(command.query)
            is Command.LaunchPlayStore -> launchPlayStore()
            is Command.ShowAppEnjoymentPrompt -> showAppEnjoymentDialog(command.promptCount)
            is Command.ShowAppRatingPrompt -> showAppRatingDialog(command.promptCount)
            is Command.ShowAppFeedbackPrompt -> showGiveFeedbackDialog(command.promptCount)
            is Command.LaunchFeedbackView -> startActivity(FeedbackActivity.intent(this))
            is Command.OpenSavedSite -> currentTab?.submitQuery(command.url)
        }
    }

    private fun launchNewSearch(intent: Intent): Boolean {
        return intent.getBooleanExtra(NEW_SEARCH_EXTRA, false)
    }

    fun launchPrivacyDashboard() {
        currentTab?.tabId?.let {
            val params = PrivacyDashboardHybridWithTabIdParam(it)
            val intent = globalActivityStarter.startIntent(this, params)
            intent?.let { startActivity(it) }
        }
    }

    fun launchFire() {
        pixel.fire(AppPixelName.FORGET_ALL_PRESSED_BROWSING)
        val dialog = FireDialog(
            context = this,
            clearPersonalDataAction = clearPersonalDataAction,
            ctaViewModel = ctaViewModel,
            pixel = pixel,
            settingsDataStore = settingsDataStore,
            userEventsStore = userEventsStore,
            appCoroutineScope = appCoroutineScope,
            dispatcherProvider = dispatcherProvider,
        )
        dialog.clearStarted = {
            removeObservers()
        }
        dialog.setOnShowListener { currentTab?.onFireDialogVisibilityChanged(isVisible = true) }
        dialog.setOnCancelListener {
            pixel.fire(if (dialog.ctaVisible) FIRE_DIALOG_PROMOTED_CANCEL else FIRE_DIALOG_CANCEL)
            currentTab?.onFireDialogVisibilityChanged(isVisible = false)
        }
        dialog.show()
    }

    fun launchNewTab() {
        lifecycleScope.launch { viewModel.onNewTabRequested() }
    }

    fun openInNewTab(
        query: String,
        sourceTabId: String?,
    ) {
        lifecycleScope.launch {
            viewModel.onOpenInNewTabRequested(query = query, sourceTabId = sourceTabId)
        }
    }

    fun openMessageInNewTab(
        message: Message,
        sourceTabId: String?,
    ) {
        openMessageInNewTabJob = lifecycleScope.launch {
            val tabId = viewModel.onNewTabRequested(sourceTabId = sourceTabId)
            val fragment = openNewTab(tabId, null, false)
            fragment.messageFromPreviousTab = message
        }
    }

    fun launchSettings() {
        startActivity(SettingsActivity.intent(this))
    }

    fun launchSitePermissionsSettings() {
        startActivity(SitePermissionsActivity.intent(this))
    }

    fun launchBookmarks() {
        startBookmarksActivityForResult.launch(globalActivityStarter.startIntent(this, BookmarksScreenNoParams))
    }

    fun launchDownloads() {
        startActivity(DownloadsActivity.intent(this))
    }

    private fun configureOnBackPressedListener() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentTab?.onBackPressed() != true) {
                        // signal user press back button to exit the app so that BrowserApplicationStateInfo
                        // can call the right callback
                        destroyedByBackPress = true
                        isEnabled = false
                        this@BrowserActivity.onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
    }

    override fun onAttachFragment(fragment: androidx.fragment.app.Fragment) {
        super.onAttachFragment(fragment)
        hideMockupOmnibar()
    }

    private fun hideMockupOmnibar() {
        // Delaying this code to avoid race condition when fragment and activity recreated
        Handler().postDelayed(
            {
                if (this::toolbarMockupBinding.isInitialized) {
                    toolbarMockupBinding.appBarLayoutMockup.visibility = View.GONE
                }
            },
            300,
        )
    }

    companion object {

        fun intent(
            context: Context,
            queryExtra: String? = null,
            newSearch: Boolean = false,
            notifyDataCleared: Boolean = false,
        ): Intent {
            val intent = Intent(context, BrowserActivity::class.java)
            intent.putExtra(EXTRA_TEXT, queryExtra)
            intent.putExtra(NEW_SEARCH_EXTRA, newSearch)
            intent.putExtra(NOTIFY_DATA_CLEARED_EXTRA, notifyDataCleared)
            return intent
        }

        const val FAVORITES_ONBOARDING_EXTRA = "FAVORITES_ONBOARDING_EXTRA"
        const val NEW_SEARCH_EXTRA = "NEW_SEARCH_EXTRA"
        const val PERFORM_FIRE_ON_ENTRY_EXTRA = "PERFORM_FIRE_ON_ENTRY_EXTRA"
        const val NOTIFY_DATA_CLEARED_EXTRA = "NOTIFY_DATA_CLEARED_EXTRA"
        const val LAUNCH_FROM_DEFAULT_BROWSER_DIALOG = "LAUNCH_FROM_DEFAULT_BROWSER_DIALOG"
        const val LAUNCH_FROM_FAVORITES_WIDGET = "LAUNCH_FROM_FAVORITES_WIDGET"
        const val LAUNCH_FROM_NOTIFICATION_PIXEL_NAME = "LAUNCH_FROM_NOTIFICATION_PIXEL_NAME"

        private const val APP_ENJOYMENT_DIALOG_TAG = "AppEnjoyment"

        private const val MAX_ACTIVE_TABS = 40
    }

    inner class BrowserStateRenderer {

        private var lastSeenBrowserState: BrowserViewModel.ViewState? = null
        private var processedOriginalIntent = false

        fun renderBrowserViewState(viewState: BrowserViewModel.ViewState) {
            renderIfChanged(viewState, lastSeenBrowserState) {
                lastSeenBrowserState = viewState

                if (viewState.hideWebContent) {
                    hideWebContent()
                } else {
                    showWebContent()
                }
            }
        }

        private fun showWebContent() {
            Timber.d("BrowserActivity can now start displaying web content. instance state is $instanceStateBundles")
            configureObservers()
            binding.clearingInProgressView.gone()

            if (lastIntent != null) {
                Timber.i("There was a deferred intent to process; handling now")
                launchNewSearchOrQuery(lastIntent)
                lastIntent = null
                return
            }

            if (!processedOriginalIntent && instanceStateBundles?.originalInstanceState == null && !intent.launchedFromRecents) {
                Timber.i("Original instance state is null, so will inspect intent for actions to take. $intent")
                launchNewSearchOrQuery(intent)
                processedOriginalIntent = true
            }
        }
    }

    private val Intent.launchedFromRecents: Boolean
        get() = (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

    private fun showAppEnjoymentDialog(promptCount: PromptCount) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.appEnjoymentDialogTitle)
            .setMessage(R.string.appEnjoymentDialogMessage)
            .setCancellable(true)
            .setPositiveButton(R.string.appEnjoymentDialogPositiveButton)
            .setNegativeButton(R.string.appEnjoymentDialogNegativeButton)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onUserSelectedAppIsEnjoyed(promptCount)
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onUserSelectedAppIsNotEnjoyed(promptCount)
                    }

                    override fun onDialogShown() {
                        viewModel.onAppEnjoymentDialogShown(promptCount)
                    }

                    override fun onDialogCancelled() {
                        viewModel.onUserCancelledAppEnjoymentDialog(promptCount)
                    }
                },
            )
            .show()
    }

    private fun showAppRatingDialog(promptCount: PromptCount) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.rateAppDialogTitle)
            .setMessage(R.string.rateAppDialogMessage)
            .setCancellable(true)
            .setPositiveButton(R.string.rateAppDialogPositiveButton)
            .setNegativeButton(R.string.rateAppDialogNegativeButton)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onUserSelectedToRateApp(promptCount)
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onUserDeclinedToRateApp(promptCount)
                    }

                    override fun onDialogShown() {
                        viewModel.onAppRatingDialogShown(promptCount)
                    }
                    override fun onDialogCancelled() {
                        viewModel.onUserCancelledRateAppDialog(promptCount)
                    }
                },
            )
            .show()
    }

    private fun showGiveFeedbackDialog(promptCount: PromptCount) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.giveFeedbackDialogTitle)
            .setMessage(R.string.giveFeedbackDialogMessage)
            .setCancellable(true)
            .setPositiveButton(R.string.giveFeedbackDialogPositiveButton)
            .setNegativeButton(R.string.giveFeedbackDialogNegativeButton)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onUserSelectedToGiveFeedback(promptCount)
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onUserDeclinedToGiveFeedback(promptCount)
                    }

                    override fun onDialogShown() {
                        viewModel.onGiveFeedbackDialogShown(promptCount)
                    }
                    override fun onDialogCancelled() {
                        viewModel.onUserCancelledGiveFeedbackDialog(promptCount)
                    }
                },
            )
            .show()
    }

    private fun hideWebContent() {
        Timber.d("Hiding web view content")
        removeObservers()
        binding.clearingInProgressView.show()
    }

    private fun launchPlayStore() {
        playStoreUtils.launchPlayStore()
    }

    private data class CombinedInstanceState(
        val originalInstanceState: Bundle?,
        val newInstanceState: Bundle?,
    )
}

// Temporary class to keep track of latest visited tabs, keeping unique ids.
private class TabList() : ArrayList<String>() {
    override fun add(element: String): Boolean {
        if (this.contains(element)) {
            this.remove(element)
        }
        return super.add(element)
    }
}
