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
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.BrowserViewModel.Command.Query
import com.duckduckgo.app.browser.BrowserViewModel.Command.ShowSystemDefaultAppsActivity
import com.duckduckgo.app.browser.BrowserViewModel.Command.ShowSystemDefaultBrowserDialog
import com.duckduckgo.app.browser.databinding.ActivityBrowserBinding
import com.duckduckgo.app.browser.databinding.IncludeOmnibarToolbarMockupBinding
import com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.DefaultBrowserBottomSheetDialog
import com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.DefaultBrowserBottomSheetDialog.EventListener
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition.BOTTOM
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition.TOP
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder
import com.duckduckgo.app.browser.tabs.TabManager
import com.duckduckgo.app.browser.tabs.adapter.TabPagerAdapter
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.downloads.DownloadsScreens.DownloadsScreenNoParams
import com.duckduckgo.app.feedback.ui.common.FeedbackActivity
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.fire.DataClearerForegroundAppRestartPixel
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.ApplicationClearDataState
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.intentText
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.global.sanitize
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.global.view.renderIfChanged
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPage
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_CANCEL
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.emailprotection.EmailProtectionLinkVerifier
import com.duckduckgo.browser.api.ui.BrowserScreens.BookmarksScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksActivity.Companion.SAVED_SITE_URL_EXTRA
import com.duckduckgo.site.permissions.impl.ui.SitePermissionScreenNoParams
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    @Inject
    lateinit var fireButtonStore: FireButtonStore

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var maliciousSiteBlockerWebViewIntegration: RealMaliciousSiteBlockerWebViewIntegration

    @Inject
    lateinit var swipingTabsFeature: SwipingTabsFeatureProvider

    @Inject
    lateinit var tabManager: TabManager

    private val lastActiveTabs = TabList()

    private var _currentTab: BrowserTabFragment? = null
    private var currentTab: BrowserTabFragment?
        get() {
            return if (swipingTabsFeature.isEnabled) {
                tabPagerAdapter.currentFragment
            } else {
                _currentTab
            }
        }
        set(value) {
            _currentTab = value
        }

    private val viewModel: BrowserViewModel by bindViewModel()

    private var instanceStateBundles: CombinedInstanceState? = null

    private var lastIntent: Intent? = null

    private lateinit var renderer: BrowserStateRenderer

    private val binding: ActivityBrowserBinding by viewBinding()

    private val tabPager: ViewPager2 by lazy {
        binding.tabPager
    }

    private val tabPagerAdapter by lazy {
        TabPagerAdapter(
            fragmentManager = supportFragmentManager,
            lifecycleOwner = this,
            activityIntent = intent,
            getSelectedTabId = tabManager::getSelectedTabId,
            getTabById = ::getTabById,
            requestAndWaitForNewTab = ::requestAndWaitForNewTab,
        )
    }

    private lateinit var toolbarMockupBinding: IncludeOmnibarToolbarMockupBinding

    private var openMessageInNewTabJob: Job? = null

    private val onTabPageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private val TOUCH_DELAY_MS = 200L
        private var wasSwipingStarted = false

        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            if (wasSwipingStarted) {
                wasSwipingStarted = false

                viewModel.onTabsSwiped()
                onTabPageSwiped(position)

                enableWebViewScrolling()
            }
        }

        private fun enableWebViewScrolling() {
            // ViewPager2 requires an artificial tap to disable intercepting touch events and enable nested scrolling
            val time = SystemClock.uptimeMillis()
            val motionEvent = MotionEvent.obtain(time, time + 1, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

            tabPager.postDelayed(TOUCH_DELAY_MS) {
                tabPager.dispatchTouchEvent(motionEvent)
            }
        }

        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)
            if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                wasSwipingStarted = true
            }
        }
    }

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

    private val startDefaultBrowserSystemDialogForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                viewModel.onSystemDefaultBrowserDialogSuccess()
            } else {
                viewModel.onSystemDefaultBrowserDialogCanceled()
            }
        }

    private val startDefaultAppsSystemActivityForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onSystemDefaultAppsActivityClosed()
        }

    private var setAsDefaultBrowserDialog: DefaultBrowserBottomSheetDialog? = null

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

        toolbarMockupBinding = when (settingsDataStore.omnibarPosition) {
            TOP -> {
                binding.bottomMockupToolbar.appBarLayoutMockup.gone()
                binding.topMockupToolbar
            }

            BOTTOM -> {
                binding.topMockupToolbar.appBarLayoutMockup.gone()
                binding.bottomMockupToolbar
            }
        }

        setContentView(binding.root)

        initializeTabs()

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

        if (swipingTabsFeature.isEnabled) {
            binding.tabPager.adapter = null
            binding.tabPager.unregisterOnPageChangeCallback(onTabPageChangeListener)
        }

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
        isExternal: Boolean,
    ): BrowserTabFragment {
        Timber.i("Opening new tab, url: $url, tabId: $tabId")
        val fragment = BrowserTabFragment.newInstance(tabId, url, skipHome, isExternal)
        addOrReplaceNewTab(fragment, tabId)
        currentTab = fragment
        return fragment
    }

    private fun addOrReplaceNewTab(
        fragment: BrowserTabFragment,
        tabId: String,
    ) {
        if (supportFragmentManager.isStateSaved) {
            return
        }
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

        viewModel.onTabActivated(tab.tabId)

        val fragment = supportFragmentManager.findFragmentByTag(tab.tabId) as? BrowserTabFragment
        if (fragment == null) {
            openNewTab(tab.tabId, tab.url, tab.skipHome, intent?.getBooleanExtra(LAUNCH_FROM_EXTERNAL_EXTRA, false) ?: false)
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

    override fun onKeyLongPress(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
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
            Timber.i("launch from default browser")
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

        if (emailProtectionLinkVerifier.shouldDelegateToInContextView(intent.intentText, currentTab?.inContextEmailProtectionShowing)) {
            currentTab?.showEmailProtectionInContextWebFlow(intent.intentText)
            Timber.v("Verification link was consumed, so don't allow it to open in a new tab")
            return
        }

        // the BrowserActivity will automatically clear its stack of activities when being brought to the foreground, so this can no longer be true
        currentTab?.inContextEmailProtectionShowing = false

        if (launchNewSearch(intent)) {
            Timber.w("new tab requested")
            launchNewTab()
            return
        }

        val existingTabId = intent.getStringExtra(OPEN_EXISTING_TAB_ID_EXTRA)
        if (existingTabId != null) {
            openExistingTab(existingTabId)
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
            } else if (intent.getBooleanExtra(OPEN_IN_CURRENT_TAB_EXTRA, false)) {
                Timber.w("open in current tab requested")
                if (currentTab != null) {
                    currentTab?.submitQuery(sharedText)
                } else {
                    Timber.w("can't use current tab, opening in new tab instead")
                    if (swipingTabsFeature.isEnabled) {
                        launchNewTab(query = sharedText, skipHome = true)
                    } else {
                        lifecycleScope.launch { viewModel.onOpenInNewTabRequested(query = sharedText, skipHome = true) }
                    }
                }
            } else {
                val isExternal = intent.getBooleanExtra(LAUNCH_FROM_EXTERNAL_EXTRA, false)
                val interstitialScreen = intent.getBooleanExtra(LAUNCH_FROM_INTERSTITIAL_EXTRA, false)
                Timber.w("opening in new tab requested for $sharedText isExternal $isExternal interstitial $interstitialScreen")
                if (!interstitialScreen) {
                    Timber.w("not launching from interstitial screen")
                    viewModel.launchFromThirdParty()
                }
                val selectedText = intent.getBooleanExtra(SELECTED_TEXT_EXTRA, false)
                val sourceTabId = if (selectedText) currentTab?.tabId else null
                val skipHome = !selectedText
                if (swipingTabsFeature.isEnabled) {
                    launchNewTab(query = sharedText, sourceTabId = sourceTabId, skipHome = skipHome)
                } else {
                    lifecycleScope.launch { viewModel.onOpenInNewTabRequested(sourceTabId = sourceTabId, query = sharedText, skipHome = skipHome) }
                }
            }
        } else {
            Timber.i("shared text empty, defaulting to show on app launch option")
            if (!intent.getBooleanExtra(LAUNCH_FROM_CLEAR_DATA_ACTION, false)) {
                if (intent.getBooleanExtra(LAUNCH_FROM_DEDICATED_WEBVIEW, false)) {
                    pixel.fire(AppPixelName.DEDICATED_WEBVIEW_NEW_TAB_OPENING)
                }
                viewModel.handleShowOnAppLaunchOption()
            }
        }
    }

    private fun configureObservers() {
        viewModel.command.observe(this) {
            processCommand(it)
        }

        if (swipingTabsFeature.isEnabled) {
            lifecycleScope.launch {
                viewModel.tabsFlow.flowWithLifecycle(lifecycle).collectLatest {
                    tabManager.onTabsChanged(it)
                }
            }

            lifecycleScope.launch {
                viewModel.selectedTabFlow.flowWithLifecycle(lifecycle).collectLatest {
                    tabManager.onSelectedTabChanged(it)
                }
            }

            lifecycleScope.launch {
                viewModel.selectedTabIndex.flowWithLifecycle(lifecycle).collectLatest {
                    onMoveToTabRequested(it)
                }
            }
        } else {
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
    }

    private fun removeObservers() {
        viewModel.command.removeObservers(this)

        if (!swipingTabsFeature.isEnabled) {
            viewModel.selectedTab.removeObservers(this)
            viewModel.tabs.removeObservers(this)
        }
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
            is Command.SwitchToTab -> openExistingTab(command.tabId)
            is Command.OpenInNewTab -> launchNewTab(command.url)
            is Command.OpenSavedSite -> currentTab?.submitQuery(command.url)
            is Command.ShowSetAsDefaultBrowserDialog -> showSetAsDefaultBrowserDialog()
            is Command.HideSetAsDefaultBrowserDialog -> hideSetAsDefaultBrowserDialog()
            is ShowSystemDefaultAppsActivity -> showSystemDefaultAppsActivity(command.intent)
            is ShowSystemDefaultBrowserDialog -> showSystemDefaultBrowserDialog(command.intent)
        }
    }

    private fun launchNewSearch(intent: Intent): Boolean {
        return intent.getBooleanExtra(NEW_SEARCH_EXTRA, false)
    }

    fun clearTabsAndRecreate() {
        tabPagerAdapter.clearFragments()
        recreate()
    }

    fun launchFire() {
        pixel.fire(AppPixelName.FORGET_ALL_PRESSED_BROWSING)
        val dialog = FireDialog(
            context = this,
            clearPersonalDataAction = clearPersonalDataAction,
            pixel = pixel,
            settingsDataStore = settingsDataStore,
            userEventsStore = userEventsStore,
            appCoroutineScope = appCoroutineScope,
            dispatcherProvider = dispatcherProvider,
            fireButtonStore = fireButtonStore,
            appBuildConfig = appBuildConfig,
        )
        dialog.clearStarted = {
            removeObservers()
        }
        dialog.setOnShowListener { currentTab?.onFireDialogVisibilityChanged(isVisible = true) }
        dialog.setOnCancelListener {
            pixel.fire(FIRE_DIALOG_CANCEL)
            currentTab?.onFireDialogVisibilityChanged(isVisible = false)
        }
        dialog.show()
    }

    fun launchSettings() {
        startActivity(SettingsActivity.intent(this))
    }

    fun launchSitePermissionsSettings() {
        globalActivityStarter.start(this, SitePermissionScreenNoParams)
    }

    fun launchBookmarks() {
        startBookmarksActivityForResult.launch(globalActivityStarter.startIntent(this, BookmarksScreenNoParams))
    }

    fun launchDownloads() {
        globalActivityStarter.start(this, DownloadsScreenNoParams)
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
        Handler(Looper.getMainLooper()).postDelayed(
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
            openInCurrentTab: Boolean = false,
            selectedText: Boolean = false,
            isExternal: Boolean = false,
            interstitialScreen: Boolean = false,
            openExistingTabId: String? = null,
            isLaunchFromClearDataAction: Boolean = false,
            isLaunchFromDedicatedWebView: Boolean = false,
        ): Intent {
            val intent = Intent(context, BrowserActivity::class.java)
            intent.putExtra(EXTRA_TEXT, queryExtra)
            intent.putExtra(NEW_SEARCH_EXTRA, newSearch)
            intent.putExtra(NOTIFY_DATA_CLEARED_EXTRA, notifyDataCleared)
            intent.putExtra(OPEN_IN_CURRENT_TAB_EXTRA, openInCurrentTab)
            intent.putExtra(SELECTED_TEXT_EXTRA, selectedText)
            intent.putExtra(LAUNCH_FROM_EXTERNAL_EXTRA, isExternal)
            intent.putExtra(LAUNCH_FROM_INTERSTITIAL_EXTRA, interstitialScreen)
            intent.putExtra(OPEN_EXISTING_TAB_ID_EXTRA, openExistingTabId)
            intent.putExtra(LAUNCH_FROM_CLEAR_DATA_ACTION, isLaunchFromClearDataAction)
            intent.putExtra(LAUNCH_FROM_DEDICATED_WEBVIEW, isLaunchFromDedicatedWebView)
            return intent
        }

        const val NEW_SEARCH_EXTRA = "NEW_SEARCH_EXTRA"
        const val PERFORM_FIRE_ON_ENTRY_EXTRA = "PERFORM_FIRE_ON_ENTRY_EXTRA"
        const val NOTIFY_DATA_CLEARED_EXTRA = "NOTIFY_DATA_CLEARED_EXTRA"
        const val LAUNCH_FROM_DEFAULT_BROWSER_DIALOG = "LAUNCH_FROM_DEFAULT_BROWSER_DIALOG"
        const val LAUNCH_FROM_FAVORITES_WIDGET = "LAUNCH_FROM_FAVORITES_WIDGET"
        const val LAUNCH_FROM_NOTIFICATION_PIXEL_NAME = "LAUNCH_FROM_NOTIFICATION_PIXEL_NAME"
        const val OPEN_IN_CURRENT_TAB_EXTRA = "OPEN_IN_CURRENT_TAB_EXTRA"
        const val SELECTED_TEXT_EXTRA = "SELECTED_TEXT_EXTRA"
        private const val LAUNCH_FROM_INTERSTITIAL_EXTRA = "INTERSTITIAL_SCREEN_EXTRA"
        const val OPEN_EXISTING_TAB_ID_EXTRA = "OPEN_EXISTING_TAB_ID_EXTRA"

        const val LAUNCH_FROM_EXTERNAL_EXTRA = "LAUNCH_FROM_EXTERNAL_EXTRA"
        private const val LAUNCH_FROM_CLEAR_DATA_ACTION = "LAUNCH_FROM_CLEAR_DATA_ACTION"
        private const val LAUNCH_FROM_DEDICATED_WEBVIEW = "LAUNCH_FROM_DEDICATED_WEBVIEW"

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

                if (swipingTabsFeature.isEnabled) {
                    tabPager.isUserInputEnabled = viewState.isTabSwipingEnabled
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

    private fun initializeTabs() {
        if (swipingTabsFeature.isEnabled) {
            tabManager.registerCallbacks(
                onTabsUpdated = ::onTabsUpdated,
            )

            tabPager.adapter = tabPagerAdapter
            tabPager.registerOnPageChangeCallback(onTabPageChangeListener)
            tabPager.setPageTransformer(MarginPageTransformer(resources.getDimension(com.duckduckgo.mobile.android.R.dimen.keyline_1).toPx().toInt()))
        }

        binding.fragmentContainer.isVisible = !swipingTabsFeature.isEnabled
        tabPager.isVisible = swipingTabsFeature.isEnabled
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

    private fun onMoveToTabRequested(index: Int) {
        tabPager.post {
            tabPager.setCurrentItem(index, false)
        }
    }

    private fun onTabPageSwiped(newPosition: Int) = lifecycleScope.launch {
        val tabId = tabPagerAdapter.getTabIdAtPosition(newPosition)
        if (tabId != null) {
            tabManager.switchToTab(tabId)
        }
    }

    private fun onTabsUpdated(updatedTabIds: List<String>) {
        tabPagerAdapter.onTabsUpdated(updatedTabIds)
    }

    private fun getTabById(tabId: String): TabEntity? = runBlocking {
        return@runBlocking tabManager.getTabById(tabId)
    }

    private fun requestAndWaitForNewTab(): TabEntity = runBlocking {
        return@runBlocking tabManager.requestAndWaitForNewTab()
    }

    fun launchNewTab(query: String? = null, sourceTabId: String? = null, skipHome: Boolean = false) {
        lifecycleScope.launch {
            if (swipingTabsFeature.isEnabled) {
                tabManager.openNewTab(query, sourceTabId, skipHome)
            } else {
                viewModel.onNewTabRequested()
            }
        }
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
            if (swipingTabsFeature.isEnabled) {
                tabPagerAdapter.setMessageForNewFragment(message)
                tabManager.openNewTab(sourceTabId)
            } else {
                val tabId = viewModel.onNewTabRequested(sourceTabId = sourceTabId)
                val fragment = openNewTab(tabId, null, false, intent?.getBooleanExtra(LAUNCH_FROM_EXTERNAL_EXTRA, false) ?: false)
                fragment.messageFromPreviousTab = message
            }
        }
    }

    fun openExistingTab(tabId: String) = lifecycleScope.launch {
        if (swipingTabsFeature.isEnabled) {
            tabManager.switchToTab(tabId)
        } else {
            viewModel.onTabSelected(tabId)
        }
    }

    fun onEditModeChanged(isInEditMode: Boolean) {
        viewModel.onOmnibarEditModeChanged(isInEditMode)
    }

    private data class CombinedInstanceState(
        val originalInstanceState: Bundle?,
        val newInstanceState: Bundle?,
    )

    private fun showSetAsDefaultBrowserDialog() {
        val dialog = DefaultBrowserBottomSheetDialog(context = this)
        dialog.eventListener = object : EventListener {
            override fun onShown() {
                viewModel.onSetDefaultBrowserDialogShown()
            }

            override fun onDismissed() {
                viewModel.onSetDefaultBrowserDismissed()
            }

            override fun onSetBrowserButtonClicked() {
                viewModel.onSetDefaultBrowserConfirmationButtonClicked()
            }

            override fun onNotNowButtonClicked() {
                viewModel.onSetDefaultBrowserNotNowButtonClicked()
            }
        }
        dialog.show()
        setAsDefaultBrowserDialog = dialog
    }

    private fun hideSetAsDefaultBrowserDialog() {
        setAsDefaultBrowserDialog?.dismiss()
        setAsDefaultBrowserDialog = null
    }

    private fun showSystemDefaultAppsActivity(intent: Intent) {
        try {
            startDefaultAppsSystemActivityForResult.launch(intent)
            viewModel.onSystemDefaultAppsActivityOpened()
        } catch (ex: Exception) {
            Timber.e(ex)
        }
    }

    private fun showSystemDefaultBrowserDialog(intent: Intent) {
        try {
            startDefaultBrowserSystemDialogForResult.launch(intent)
            viewModel.onSystemDefaultBrowserDialogShown()
        } catch (ex: Exception) {
            Timber.e(ex)
        }
    }
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
