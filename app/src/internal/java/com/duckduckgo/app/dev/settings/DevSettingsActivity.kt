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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.Toast
import androidx.annotation.MenuRes
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityDevSettingsBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.dev.settings.DevSettingsViewModel.Command
import com.duckduckgo.app.dev.settings.db.UAOverride
import com.duckduckgo.app.dev.settings.privacy.TrackerDataDevReceiver.Companion.DOWNLOAD_TDS_INTENT_ACTION
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.lang.IllegalStateException

class DevSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityDevSettingsBinding by viewBinding()

    private val viewModel: DevSettingsViewModel by bindViewModel()

    private val nextTdsToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onNextTdsToggled(isChecked)
    }

    private val overrideUAListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onOverrideUAToggled(isChecked)
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
        binding.privacyTest1.setOnClickListener { viewModel.goToPrivacyTest1() }
        binding.privacyTest2.setOnClickListener { viewModel.goToPrivacyTest2() }
        binding.overrideUserAgentSelector.setOnClickListener { viewModel.onUserAgentSelectorClicked() }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState ->
                viewState.let {
                    binding.nextTdsEnabled.quietlySetIsChecked(it.nextTdsEnabled, nextTdsToggleListener)
                    binding.overrideUserAgentToggle.quietlySetIsChecked(it.overrideUA, overrideUAListener)
                    binding.overrideUserAgentSelector.isEnabled = it.overrideUA
                    binding.overrideUserAgentSelector.setSubtitle(it.userAgent)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is Command.SendTdsIntent -> sendTdsIntent()
            is Command.GoToUrl -> goToUrl(it.url)
            is Command.OpenUASelector -> showUASelector(R.menu.user_agent_menu)
            else -> TODO()
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

    private fun showUASelector(@MenuRes popupMenu: Int) {
        val popup = PopupMenu(this, binding.overrideUserAgentSelector)
        popup.menuInflater.inflate(popupMenu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            val userAgent = when (menuItem.itemId) {
                R.id.noAppId -> UAOverride.NO_APP_ID
                R.id.noVersion -> UAOverride.NO_VERSION
                R.id.chrome -> UAOverride.CHROME
                R.id.firefox -> UAOverride.FIREFOX
                R.id.duckDuckGo -> UAOverride.DDG
                R.id.webView -> UAOverride.WEBVIEW
                else -> throw IllegalStateException()
            }
            viewModel.onUserAgentSelected(userAgent)
            true
        }
        popup.setOnDismissListener { }
        popup.show()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DevSettingsActivity::class.java)
        }
    }
}
