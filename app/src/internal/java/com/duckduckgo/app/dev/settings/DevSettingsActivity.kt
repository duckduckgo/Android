/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.dev.settings

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.R.layout
import com.duckduckgo.app.browser.databinding.ActivityDevSettingsBinding
import com.duckduckgo.app.browser.webview.WebContentDebuggingFeature
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command.ChangePrivacyConfigUrl
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command.CustomTabs
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command.Notifications
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command.OpenUASelector
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command.SendTdsIntent
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command.ShowSavedSitesClearedConfirmation
import com.duckduckgo.app.dev.settings.customtabs.CustomTabsInternalSettingsActivity
import com.duckduckgo.app.dev.settings.db.UAOverride
import com.duckduckgo.app.dev.settings.notifications.NotificationsActivity
import com.duckduckgo.app.dev.settings.privacy.TrackerDataDevReceiver.Companion.DOWNLOAD_TDS_INTENT_ACTION
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalSettingsActivity
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class DevSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var webContentDebuggingFeature: WebContentDebuggingFeature

    private val binding: ActivityDevSettingsBinding by viewBinding()

    private val viewModel: DevSettingsViewModel by bindViewModel()

    private val startupTraceToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onStartupTraceToggled(isChecked)
    }

    private val overrideUAListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onOverrideUAToggled(isChecked)
    }

    private val surveySandboxListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onSandboxSurveyToggled(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun configureUiEventHandlers() {
        binding.enableWebContentDebugging.quietlySetIsChecked(webContentDebuggingFeature.webContentDebugging().isEnabled()) { _, isChecked ->
            webContentDebuggingFeature.webContentDebugging().setRawStoredState(Toggle.State(enable = isChecked))
        }
        binding.triggerAnr.setOnClickListener {
            Handler(Looper.getMainLooper()).post {
                Thread.sleep(10000)
            }
        }
        binding.clearSavedSites.setOnClickListener {
            viewModel.clearSavedSites()
        }
        binding.overrideUserAgentSelector.setOnClickListener { viewModel.onUserAgentSelectorClicked() }
        binding.overridePrivacyRemoteConfigUrl.setOnClickListener { viewModel.onRemotePrivacyUrlClicked() }
        binding.customTabs.setOnClickListener { viewModel.customTabsClicked() }
        binding.notifications.setOnClickListener { viewModel.notificationsClicked() }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState ->
                viewState.let {
                    binding.enableAppStartupTrace.quietlySetIsChecked(it.startupTraceEnabled, startupTraceToggleListener)
                    binding.overrideUserAgentToggle.quietlySetIsChecked(it.overrideUA, overrideUAListener)
                    binding.overrideUserAgentSelector.isEnabled = it.overrideUA
                    binding.overrideUserAgentSelector.setSecondaryText(it.userAgent)
                    binding.useSandboxSurvey.quietlySetIsChecked(it.useSandboxSurvey, surveySandboxListener)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is SendTdsIntent -> sendTdsIntent()
            is OpenUASelector -> showUASelector()
            is ShowSavedSitesClearedConfirmation -> showSavedSitesClearedConfirmation()
            is ChangePrivacyConfigUrl -> showChangePrivacyUrl()
            is CustomTabs -> showCustomTabs()
            Notifications -> showNotifications()
        }
    }

    private fun goToUrl(url: String) {
        startActivity(BrowserActivity.intent(this, url))
        finish()
    }

    private fun sendTdsIntent() {
        Toast.makeText(this, getString(R.string.devSettingsScreenTdsWait), Toast.LENGTH_SHORT).show()
        val intent = Intent()
        intent.action = DOWNLOAD_TDS_INTENT_ACTION
        sendBroadcast(intent)
    }

    private fun showUASelector() {
        val popup = PopupMenu(layoutInflater, layout.popup_window_user_agent_override)
        val view = popup.contentView
        popup.apply {
            onMenuItemClicked(view.findViewById(R.id.firefox)) { viewModel.onUserAgentSelected(UAOverride.FIREFOX) }
            onMenuItemClicked(view.findViewById(R.id.defaultUA)) { viewModel.onUserAgentSelected(UAOverride.DEFAULT) }
            onMenuItemClicked(view.findViewById(R.id.webView)) { viewModel.onUserAgentSelected(UAOverride.WEBVIEW) }
        }
        popup.show(binding.root, binding.overrideUserAgentSelector)
    }

    private fun showSavedSitesClearedConfirmation() {
        Toast.makeText(this, getString(R.string.devSettingsClearSavedSitesConfirmation), Toast.LENGTH_SHORT).show()
    }

    private fun showChangePrivacyUrl() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(PrivacyConfigInternalSettingsActivity.intent(this), options)
    }

    private fun showCustomTabs() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(CustomTabsInternalSettingsActivity.intent(this), options)
    }

    private fun showNotifications() {
        startActivity(NotificationsActivity.intent(this))
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DevSettingsActivity::class.java)
        }
    }
}
