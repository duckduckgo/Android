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
import com.duckduckgo.app.email.ui.EmailProtectionUnsupportedScreenNoParams
import com.duckduckgo.app.firebutton.FireButtonScreenNoParams
import com.duckduckgo.app.generalsettings.GeneralSettingsScreenNoParams
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.permissions.PermissionsScreenNoParams
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.PRIVACY_PRO_IS_ENABLED_AND_ELIGIBLE
import com.duckduckgo.app.privatesearch.PrivateSearchScreenNoParams
import com.duckduckgo.app.settings.NewSettingsViewModel.Command
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAboutScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAccessibilitySettings
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAddHomeScreenWidget
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppTPOnboarding
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppTPTrackersScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppearanceScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAutofillSettings
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchCookiePopupProtectionScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchDuckChatScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchEmailProtection
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchEmailProtectionNotSupported
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchFeedback
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchFireButtonScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchGeneralSettingsScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchOtherPlatforms
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchPermissionsScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchPproUnifiedFeedback
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchPrivateSearchWebPage
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchSyncSettings
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchWebTrackingProtectionScreen
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.webtrackingprotection.WebTrackingProtectionScreenNoParams
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.impl.ui.AutoconsentSettingsActivity
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreen
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.RootSettingsNode
import com.duckduckgo.common.ui.Searchable
import com.duckduckgo.common.ui.SearchableTag
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.DaxListItem.IconSize.Small
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerActivityWithEmptyParams
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.settings.api.DuckPlayerSettingsPlugin
import com.duckduckgo.settings.api.ProSettingsPlugin
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.GeneralPrivacyProFeedbackScreenNoParams
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
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
        with(viewsPrivacy) {
            settingsPlugins.forEach { plugin ->
                settingsContent.addView(plugin.getView(this@NewSettingsActivity), 0)
            }
            privateSearchSetting.setClickListener { viewModel.onPrivateSearchSettingClicked() }
            webTrackingProtectionSetting.setClickListener { viewModel.onWebTrackingProtectionSettingClicked() }
            cookiePopupProtectionSetting.setClickListener { viewModel.onCookiePopupProtectionSettingClicked() }
            emailSetting.setClickListener { viewModel.onEmailProtectionSettingClicked() }
            vpnSetting.setClickListener { viewModel.onAppTPSettingClicked() }
        }

        with(viewsMain) {
            autofillLoginsSetting.setClickListener { viewModel.onAutofillSettingsClick() }
            syncSetting.setClickListener { viewModel.onSyncSettingClicked() }
            fireButtonSetting.setClickListener { viewModel.onFireButtonSettingClicked() }
            permissionsSetting.setClickListener { viewModel.onPermissionsSettingClicked() }
            appearanceSetting.setClickListener { viewModel.onAppearanceSettingClicked() }
            accessibilitySetting.setClickListener { viewModel.onAccessibilitySettingClicked() }
            generalSetting.setClickListener { viewModel.onGeneralSettingClicked() }
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
        assignSearchKeywords()
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
                    // needs to happen first
                    restoreHiddenBySearch()

                    updateDeviceShieldSettings(
                        it.appTrackingProtectionEnabled,
                    )
                    updateEmailSubtitle(it.emailAddress)
                    updateAutofill(it.showAutofill)
                    updateSyncSetting(visible = it.showSyncSetting)
                    updateAutoconsent(it.isAutoconsentEnabled)
                    updatePrivacyPro(it.isPrivacyProEnabled)
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

    private val hiddenBySearch = mutableListOf<View>()

    private fun restoreHiddenBySearch() {
        hiddenBySearch.forEach { hiddenView ->
            binding.includeSettings.settingsContent.children.find { it == hiddenView }?.show()
        }
        hiddenBySearch.clear()
    }

    private fun applySearchResults(searchResults: Set<UUID>?) {
        if (searchResults != null) {
            binding.includeSettings.settingsContent.children.forEach {
                if (it.isVisible && (it !is Searchable || !searchResults.contains(it.id))) {
                    it.gone()
                    hiddenBySearch.add(it)
                }
            }
        }
    }

    private fun assignSearchKeywords() {
        viewsMain.syncSetting.assignSearchKeywords(setOf(
            "sync", "backup", "cloud", "restore",
            "account", "data", "transfer", "storage",
            "link", "synchronize", "upload", "download"
        ))

        viewsMain.autofillLoginsSetting.assignSearchKeywords(setOf(
            "password", "login", "autofill", "credentials",
            "manager", "security", "vault", "biometric",
            "2fa", "two-factor", "authentication", "fingerprint",
            "face recognition", "secure", "protection"
        ))

        viewsMain.accessibilitySetting.assignSearchKeywords(setOf(
            "accessibility", "font", "font size", "zoom",
            "readability", "contrast", "display",
            "dyslexia", "vision", "screen reader", "text", "text size", "size",
            "high contrast", "magnifier", "voiceover", "assistive"
        ))

        viewsMain.appearanceSetting.assignSearchKeywords(setOf(
            "theme", "dark", "light", "night", "day",
            "color", "palette", "UI", "interface",
            "customization", "scheme", "appearance",
            "text", "font", "mode", "style", "layout", "design"
        ))
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
                        binding.includeSettings.settingsContent.children.mapNotNull {
                            if (it is Searchable) {
                                SearchableTag(id = it.id, keywords = it.generateKeywords())
                            } else null
                        },
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
                binding.includeSettings.settingsContent.layoutTransition = layoutTransition
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                binding.includeSettings.settingsContent.layoutTransition = null
                return true
            }
        })

        return true
    }

    private fun updatePrivacyPro(isPrivacyProEnabled: Boolean) {
        if (isPrivacyProEnabled) {
            pixel.fire(PRIVACY_PRO_IS_ENABLED_AND_ELIGIBLE, type = Daily())
            viewsPro.show()
        } else {
            viewsPro.gone()
        }
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

    private fun updateAutofill(autofillEnabled: Boolean) = with(viewsMain.autofillLoginsSetting) {
        visibility = if (autofillEnabled) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateEmailSubtitle(emailAddress: String?) {
        viewsPrivacy.emailSetting.setStatus(isOn = !emailAddress.isNullOrEmpty())
    }

    private fun updateSyncSetting(visible: Boolean) {
        with(viewsMain.syncSetting) {
            isVisible = visible
        }
    }

    private fun updateAutoconsent(enabled: Boolean) {
        viewsPrivacy.cookiePopupProtectionSetting.setStatus(isOn = enabled)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is LaunchAutofillSettings -> launchScreen(AutofillSettingsScreen(source = AutofillSettingsLaunchSource.SettingsActivity))
            is LaunchAccessibilitySettings -> launchScreen(AccessibilityScreens.Default)
            is LaunchAppTPTrackersScreen -> launchScreen(AppTrackerActivityWithEmptyParams)
            is LaunchAppTPOnboarding -> launchScreen(AppTrackerOnboardingActivityWithEmptyParamsParams)
            is LaunchEmailProtection -> launchActivityAndFinish(BrowserActivity.intent(this, it.url, interstitialScreen = true))
            is LaunchEmailProtectionNotSupported -> launchScreen(EmailProtectionUnsupportedScreenNoParams)
            is LaunchAddHomeScreenWidget -> launchAddHomeScreenWidget()
            is LaunchSyncSettings -> launchScreen(SyncActivityWithEmptyParams)
            is LaunchPrivateSearchWebPage -> launchScreen(PrivateSearchScreenNoParams)
            is LaunchWebTrackingProtectionScreen -> launchScreen(WebTrackingProtectionScreenNoParams)
            is LaunchCookiePopupProtectionScreen -> launchActivity(AutoconsentSettingsActivity.intent(this))
            is LaunchFireButtonScreen -> launchScreen(FireButtonScreenNoParams)
            is LaunchPermissionsScreen -> launchScreen(PermissionsScreenNoParams)
            is LaunchDuckChatScreen -> launchScreen(DuckChatSettingsNoParams)
            is LaunchAppearanceScreen -> launchScreen(AppearanceScreen.Default)
            is LaunchAboutScreen -> launchScreen(AboutScreenNoParams)
            is LaunchGeneralSettingsScreen -> launchScreen(GeneralSettingsScreenNoParams)
            is LaunchFeedback -> launchFeedback()
            is LaunchPproUnifiedFeedback -> launchScreen(GeneralPrivacyProFeedbackScreenNoParams)
            is LaunchOtherPlatforms -> launchActivityAndFinish(BrowserActivity.intent(context = this, queryExtra = OTHER_PLATFORMS_URL))
            else -> {}
        }
    }

    private fun updateDeviceShieldSettings(appTPEnabled: Boolean) {
        viewsPrivacy.vpnSetting.setStatus(isOn = appTPEnabled)
    }

    private fun launchDefaultAppScreen() {
        launchDefaultAppActivity()
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
