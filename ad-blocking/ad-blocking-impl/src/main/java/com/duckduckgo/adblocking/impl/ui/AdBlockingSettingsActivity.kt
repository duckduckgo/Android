/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.ui

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.databinding.ActivityAdBlockingSettingsBinding
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckplayer.api.DuckPlayerSettingsNoParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AdBlockingSettingsNoParams::class)
class AdBlockingSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: AdBlockingSettingsViewModel by bindViewModel()
    private val binding: ActivityAdBlockingSettingsBinding by viewBinding()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        binding.adBlockingDescription.addClickableSpan(
            textSequence = getText(R.string.ad_blocking_settings_description),
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        viewModel.onLearnMoreClicked()
                    }
                },
            ),
        )

        binding.blockAdsToggle.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onBlockAdsToggled(isChecked)
        }

        val openDuckPlayerSettings = View.OnClickListener { viewModel.onDuckPlayerClicked() }
        binding.duckPlayerEntry.setClickListener { viewModel.onDuckPlayerClicked() }
        binding.duckPlayerDescription.setOnClickListener(openDuckPlayerSettings)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { state -> binding.blockAdsToggle.setIsChecked(state.isEnabled) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    is AdBlockingSettingsViewModel.Command.OpenLearnMore -> globalActivityStarter.start(
                        this,
                        BrowserScreens.WebViewActivityWithParams(
                            url = command.url,
                            screenTitle = getString(R.string.ad_blocking_settings_title),
                        ),
                    )
                    AdBlockingSettingsViewModel.Command.OpenDuckPlayerSettings -> globalActivityStarter.start(
                        this,
                        DuckPlayerSettingsNoParams,
                    )
                }
            }
            .launchIn(lifecycleScope)
    }
}
