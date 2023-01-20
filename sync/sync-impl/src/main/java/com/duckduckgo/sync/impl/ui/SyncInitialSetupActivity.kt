/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.databinding.ActivitySyncSetupBinding
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SyncInitialSetupActivity : DuckDuckGoActivity() {
    private val binding: ActivitySyncSetupBinding by viewBinding()
    private val viewModel: SyncInitialSetupViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeUiEvents()
        configureListeners()
    }

    private fun configureListeners() {
        binding.createAccountButton.setOnClickListener { viewModel.onCreateAccountClicked() }
        binding.storeRecoveryCodeButton.setOnClickListener { viewModel.onStoreRecoveryCodeClicked() }
        binding.resetButton.setOnClickListener { viewModel.onResetClicked() }
    }

    private fun observeUiEvents() {
        viewModel
            .viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.userIdTextView.text = viewState.userId
        binding.deviceIdTextView.text = viewState.deviceId
        binding.deviceNameTextView.text = viewState.deviceName
        binding.accountStateTextView.isVisible = viewState.isSignedIn
    }
}
