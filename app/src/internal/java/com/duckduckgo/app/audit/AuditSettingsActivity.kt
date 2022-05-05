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

package com.duckduckgo.app.audit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.audit.AuditSettingsViewModel.Command
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.COOKIES_3P_RETRIEVE
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.COOKIES_3P_STORE
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.FIRE_BUTTON_RETRIEVE
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.FIRE_BUTTON_STORE
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.GPC
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.GPC_OTHER
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.HTTPS_UPGRADES
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.REQUEST_BLOCKING
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.STEP_1
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.STEP_2
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.STEP_3
import com.duckduckgo.app.audit.AuditSettingsViewModel.Companion.SURROGATES
import com.duckduckgo.app.browser.databinding.ActivityAuditSettingsBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class AuditSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityAuditSettingsBinding by viewBinding()

    private val viewModel: AuditSettingsViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    override fun onDestroy() {
        viewModel.onDestroy()
        super.onDestroy()
    }

    private fun configureUiEventHandlers() {
        binding.step1.setOnClickListener { viewModel.goToUrl(STEP_1) }
        binding.step2.setOnClickListener { viewModel.goToUrl(STEP_2) }
        binding.step3.setOnClickListener { viewModel.goToUrl(STEP_3) }
        binding.requestBlocking.setOnClickListener { viewModel.goToUrl(REQUEST_BLOCKING) }
        binding.httpsUpgrades.setOnClickListener { viewModel.goToUrl(HTTPS_UPGRADES) }
        binding.fireButtonStore.setOnClickListener { viewModel.goToUrl(FIRE_BUTTON_STORE) }
        binding.fireButtonRetrieve.setOnClickListener { viewModel.goToUrl(FIRE_BUTTON_RETRIEVE) }
        binding.cookies3pStore.setOnClickListener { viewModel.goToUrl(COOKIES_3P_STORE) }
        binding.cookies3pRetrieve.setOnClickListener { viewModel.goToUrl(COOKIES_3P_RETRIEVE) }
        binding.gpc.setOnClickListener { viewModel.goToUrl(GPC) }
        binding.surrogates.setOnClickListener { viewModel.goToUrl(SURROGATES) }
        binding.gpcOther.setOnClickListener { viewModel.goToUrl(GPC_OTHER) }
        binding.requestBlockingDisabled.setOnClickListener { viewModel.goToUrl(REQUEST_BLOCKING, false) }
        binding.surrogatesDisabled.setOnClickListener { viewModel.goToUrl(SURROGATES, false) }
    }

    private fun observeViewModel() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is Command.GoToUrl -> goToUrl(it.url)
            else -> TODO()
        }
    }

    private fun goToUrl(url: String) {
        startActivity(BrowserActivity.intent(this, url))
        finish()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AuditSettingsActivity::class.java)
        }
    }
}
