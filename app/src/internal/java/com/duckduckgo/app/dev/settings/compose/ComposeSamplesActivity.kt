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

package com.duckduckgo.app.dev.settings.compose

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.databinding.ActivityComposeSamplesBinding
import com.duckduckgo.app.dev.settings.onboarding.relaunchIntoOnboarding
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ComposeSamplesScreen::class)
class ComposeSamplesActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityComposeSamplesBinding by viewBinding()
    private val viewModel: ComposeSamplesViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.contentScrollView)
        setupToolbar(binding.includeToolbar.toolbar)

        binding.onboardingSample.setOnClickListener { viewModel.onOnboardingSampleClicked() }

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    ComposeSamplesViewModel.Command.RelaunchIntoOnboarding -> relaunchIntoOnboarding()
                }
            }
            .launchIn(lifecycleScope)

        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { state -> binding.onboardingSample.isEnabled = !state.launching }
            .launchIn(lifecycleScope)
    }
}

data object ComposeSamplesScreen : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = ComposeSamplesScreen
}
