/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.animation.LayoutTransition
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.children
import androidx.core.view.contains
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.about.AboutScreenNoParams
import com.duckduckgo.app.about.FeedbackContract
import com.duckduckgo.app.accessibility.AccessibilityScreens
import com.duckduckgo.app.appearance.AppearanceScreen
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivitySettingsNewBinding
import com.duckduckgo.app.firebutton.FireButtonScreenNoParams
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.NewSettingsViewModel.Command
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAboutScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAccessibilitySettings
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAddHomeScreenWidget
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppearanceScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchDuckChatScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchFeedback
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchFireButtonScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchOtherPlatforms
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchPproUnifiedFeedback
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SearchStatus
import com.duckduckgo.common.ui.settings.SearchStatus.HIT
import com.duckduckgo.common.ui.settings.Searchable
import com.duckduckgo.common.ui.settings.SearchableTag
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.DaxListItem.IconSize.Small
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.settings.api.DuckPlayerSettingsPlugin
import com.duckduckgo.settings.api.ProSettingsPlugin
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.GeneralPrivacyProFeedbackScreenNoParams
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

private const val OTHER_PLATFORMS_URL = "https://duckduckgo.com/app"

@InjectWith(ActivityScope::class)
class NewSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: NewSettingsViewModel by bindViewModel()
    private val binding: ActivitySettingsNewBinding by viewBinding()

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var internalFeaturePlugins: PluginPoint<InternalFeaturePlugin>

    @Inject
    lateinit var addWidgetLauncher: AddWidgetLauncher

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var _settingsPlugins: PluginPoint<RootSettingsNode>
    private val settingsPlugins by lazy {
        _settingsPlugins.getPlugins()
    }

    @Inject
    lateinit var _proSettingsPlugin: PluginPoint<ProSettingsPlugin>
    private val proSettingsPlugin by lazy {
        _proSettingsPlugin.getPlugins()
    }

    @Inject
    lateinit var _duckPlayerSettingsPlugin: PluginPoint<DuckPlayerSettingsPlugin>
    private val duckPlayerSettingsPlugin by lazy {
        _duckPlayerSettingsPlugin.getPlugins()
    }

    private val feedbackFlow = registerForActivityResult(FeedbackContract()) { resultOk ->
        if (resultOk) {
            Snackbar.make(
                binding.root,
                R.string.thanksForTheFeedback,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    private val viewsPrivacy
        get() = binding.includeSettings

    private val viewsMain
        get() = binding.includeSettings

    private val viewsNextSteps
        get() = binding.includeSettings

    private val viewsOther
        get() = binding.includeSettings

    private val viewsInternal
        get() = binding.includeSettings.contentSettingsInternal

    private val viewsPro
        get() = binding.includeSettings.contentSettingsPrivacyPro

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        configureInternalFeatures()
        configureSettings()
        lifecycle.addObserver(viewModel)
        observeViewModel()

        intent?.getStringExtra(BrowserActivity.LAUNCH_FROM_NOTIFICATION_PIXEL_NAME)?.let {
            viewModel.onLaunchedFromNotification(it)
        }
    }

    private fun configureUiEventHandlers() {
        with(viewsMain) {
            fireButtonSetting.setClickListener { viewModel.onFireButtonSettingClicked() }
            includeDuckChatSetting.duckChatSetting.setOnClickListener { viewModel.onDuckChatSettingClicked() }
        }

        with(viewsNextSteps) {
            addWidgetToHomeScreenSetting.setOnClickListener { viewModel.userRequestedToAddHomeScreenWidget() }
            addressBarPositionSetting.setOnClickListener { viewModel.onChangeAddressBarPositionClicked() }
            enableVoiceSearchSetting.setOnClickListener { viewModel.onEnableVoiceSearchClicked() }
        }

        with(viewsOther) {
            aboutSetting.setOnClickListener { viewModel.onAboutSettingClicked() }
            shareFeedbackSetting.setOnClickListener { viewModel.onShareFeedbackClicked() }
            ddgOnOtherPlatformsSetting.setTrailingIconSize(Small)
            ddgOnOtherPlatformsSetting.setOnClickListener { viewModel.onDdgOnOtherPlatformsClicked() }
        }
    }

    private fun configureSettings() {
        if (proSettingsPlugin.isEmpty()) {
            viewsPro.gone()
        } else {
            proSettingsPlugin.forEach { plugin ->
                viewsPro.addView(plugin.getView(this))
            }
        }

        if (duckPlayerSettingsPlugin.isEmpty()) {
            viewsMain.settingsSectionDuckPlayer.gone()
        } else {
            duckPlayerSettingsPlugin.forEach { plugin ->
                viewsMain.settingsSectionDuckPlayer.addView(plugin.getView(this))
            }
        }

        settingsPlugins.forEach { plugin ->
            binding.includeSettings.searchableSettingsContent.addView(plugin.getView(this@NewSettingsActivity))
        }
        binding.includeSettings.searchableSettingsContent.children.forEach { view ->
            view.doOnAttach { attachedView ->
                attachedView.updateSearchStatus(viewModel.viewState().value.searchResults)
            }
        }
        // when activity recreates (for example during theme change),
        // we need to clean up the cached search results because they are not pointing towards view IDs that are dead
        viewModel.onSearchQueryTextChange(null, emptyList())
    }

    private fun configureInternalFeatures() {
        viewsInternal.settingsSectionInternal.visibility = if (internalFeaturePlugins.getPlugins().isEmpty()) View.GONE else View.VISIBLE
        internalFeaturePlugins.getPlugins().forEach { feature ->
            Timber.v("Adding internal feature ${feature.internalFeatureTitle()}")
            val view = TwoLineListItem(this).apply {
                setPrimaryText(feature.internalFeatureTitle())
                setSecondaryText(feature.internalFeatureSubtitle())
            }
            viewsInternal.settingsInternalFeaturesContainer.addView(view)
            view.setClickListener { feature.onInternalFeatureClicked(this) }
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .distinctUntilChanged()
            .onEach { viewState ->
                viewState.let {
                    updateDuckPlayer(it.isDuckPlayerEnabled)
                    updateDuckChat(it.isDuckChatEnabled)
                    updateVoiceSearchVisibility(it.isVoiceSearchVisible)

                    // needs to happen last
                    applySearchResults(it.searchResults)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun applySearchResults(searchResults: Set<UUID>?) {
        binding.includeSettings.searchableSettingsContent.children.forEach {
            it.updateSearchStatus(searchResults)
        }
        if (searchResults != null) {
            val hitNestedPluginsMap = searchResults.filter { result ->
                binding.includeSettings.searchableSettingsContent.children.none {
                    val searchableView = it as Searchable
                    searchableView.searchableId == result
                }
            }.mapNotNull {
                settingsPlugins.findNodeById(it)
            }.associateBy { it.categoryNameResId }

            hitNestedPluginsMap.values.forEach {
                val view = it.getView(this)
                view.doOnAttach {
                    (view as Searchable).setSearchStatus(HIT)
                }
                if (!binding.includeSettings.searchableSettingsContent.contains(view)) {
                    binding.includeSettings.searchableSettingsContent.addView(view)
                }
            }

            // clean up
            binding.includeSettings.searchableSettingsContent.children.filter { view ->
                val searchableView = view as Searchable
                val plugin = settingsPlugins.findNodeById(searchableView.searchableId)
                plugin !is RootSettingsNode && !searchResults.contains(searchableView.searchableId)
            }.forEach {
                binding.includeSettings.searchableSettingsContent.removeView(it)
            }
        } else {
            // clean up
            binding.includeSettings.searchableSettingsContent.children.filter { view ->
                val searchableView = view as Searchable
                val plugin = settingsPlugins.findNodeById(searchableView.searchableId)
                plugin !is RootSettingsNode
            }.forEach {
                binding.includeSettings.searchableSettingsContent.removeView(it)
            }
        }
    }

    private fun View.updateSearchStatus(searchResults: Set<UUID>?) {
        if (!isAttachedToWindow) {
            return
        }
        val searchableView = this as Searchable
        val status = if (searchResults != null) {
            if (searchResults.contains(searchableId)) {
                SearchStatus.HIT
            } else {
                SearchStatus.MISS
            }
        } else {
            SearchStatus.NONE
        }
        searchableView.setSearchStatus(status)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        // Configure the SearchView
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.onSearchQueryTextChange(
                        newText,
                        searchableTags = settingsPlugins.generateSearchableTags()
                    )
                    return true
                }
            },
        )

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                val layoutTransition = LayoutTransition()
                layoutTransition.enableTransitionType(LayoutTransition.APPEARING)
                layoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING)
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
                layoutTransition.enableTransitionType(LayoutTransition.CHANGE_APPEARING)
                layoutTransition.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
                binding.includeSettings.searchableSettingsContent.layoutTransition = layoutTransition
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.onSearchQueryTextChange(null, searchableTags = settingsPlugins.generateSearchableTags())
                return true
            }
        })

        return true
    }

    private fun Collection<SettingsNode>.generateSearchableTags(): List<SearchableTag> {
        fun generateTagsRecursively(node: SettingsNode): List<SearchableTag> =
            listOf(SearchableTag(node.id, node.generateKeywords())) +
                node.children.flatMap { generateTagsRecursively(it) }

        return this.flatMap { generateTagsRecursively(it) }
    }

    private fun Collection<SettingsNode>.findNodeById(id: UUID): SettingsNode? {
        fun findRecursively(node: SettingsNode): SettingsNode? =
            when {
                node.id == id -> node
                else -> node.children.asSequence().mapNotNull { findRecursively(it) }.firstOrNull()
            }

        return this.asSequence().mapNotNull { findRecursively(it) }.firstOrNull()
    }

    private fun updateDuckPlayer(isDuckPlayerEnabled: Boolean) {
        if (isDuckPlayerEnabled) {
            viewsMain.settingsSectionDuckPlayer.show()
        } else {
            viewsMain.settingsSectionDuckPlayer.gone()
        }
    }

    private fun updateDuckChat(isDuckChatEnabled: Boolean) {
        if (isDuckChatEnabled) {
            viewsMain.includeDuckChatSetting.duckChatSetting.show()
        } else {
            viewsMain.includeDuckChatSetting.duckChatSetting.gone()
        }
    }

    private fun updateVoiceSearchVisibility(isVisible: Boolean) {
        viewsNextSteps.enableVoiceSearchSetting.isVisible = isVisible
    }

    private fun processCommand(it: Command) {
        when (it) {
            is LaunchAccessibilitySettings -> launchScreen(AccessibilityScreens.Default)
            is LaunchAddHomeScreenWidget -> launchAddHomeScreenWidget()
            is LaunchFireButtonScreen -> launchScreen(FireButtonScreenNoParams)
            is LaunchDuckChatScreen -> launchScreen(DuckChatSettingsNoParams)
            is LaunchAppearanceScreen -> launchScreen(AppearanceScreen.Default)
            is LaunchAboutScreen -> launchScreen(AboutScreenNoParams)
            is LaunchFeedback -> launchFeedback()
            is LaunchPproUnifiedFeedback -> launchScreen(GeneralPrivacyProFeedbackScreenNoParams)
            is LaunchOtherPlatforms -> launchActivityAndFinish(BrowserActivity.intent(context = this, queryExtra = OTHER_PLATFORMS_URL))
        }
    }

    private fun launchScreen(activityParams: ActivityParams) {
        globalActivityStarter.start(this, activityParams)
    }

    private fun launchActivity(intent: Intent) {
        startActivity(intent)
    }

    private fun launchActivityAndFinish(intent: Intent) {
        launchActivity(intent)
        finish()
    }

    private fun launchAddHomeScreenWidget() {
        pixel.fire(AppPixelName.SETTINGS_ADD_HOME_SCREEN_WIDGET_CLICKED)
        addWidgetLauncher.launchAddWidget(this)
    }

    private fun launchFeedback() {
        feedbackFlow.launch(null)
    }

    companion object {
        const val LAUNCH_FROM_NOTIFICATION_PIXEL_NAME = "LAUNCH_FROM_NOTIFICATION_PIXEL_NAME"

        fun intent(context: Context): Intent {
            return Intent(context, NewSettingsActivity::class.java)
        }
    }
}
