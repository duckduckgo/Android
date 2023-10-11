/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.settings.geoswitching

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpGeoswitchingBinding
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpGeoSwitchingViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetpGeoswitchingScreenNoParams::class)
class NetpGeoswitchingActivity : DuckDuckGoActivity() {
    private val binding: ActivityNetpGeoswitchingBinding by viewBinding()
    private val viewModel: NetpGeoSwitchingViewModel by bindViewModel()
    private lateinit var adapter: NetpGeoswitchingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        bindViews()
        observeViewModel()
        viewModel.initialize(this)
    }

    private fun bindViews() {
        adapter = NetpGeoswitchingAdapter(
            viewModel.getSelectedCountryCode(),
            onItemMenuClicked = {

            },
            onCountrySelected = {
                viewModel.onCountrySelected(it)
            },
            onNearestAvailableSelected = {
                viewModel.onNearestAvailableCountrySelected()
            },
        )
        binding.geoswitchingRecycler.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        adapter.submitList(viewState.items)
    }
}

internal object NetpGeoswitchingScreenNoParams : GlobalActivityStarter.ActivityParams
