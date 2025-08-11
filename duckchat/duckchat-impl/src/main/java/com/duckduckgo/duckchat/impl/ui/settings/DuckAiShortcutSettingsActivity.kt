/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui.settings

import android.os.Bundle
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.databinding.ActivityDuckAiShortcutSettingsBinding
import com.duckduckgo.duckchat.impl.ui.settings.DuckAiShortcutSettingsViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class DuckAiShortcutSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: DuckAiShortcutSettingsViewModel by bindViewModel()
    private val binding: ActivityDuckAiShortcutSettingsBinding by viewBinding()

    private val menuToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.onShowDuckChatInMenuToggled(isChecked)
        }

    private val addressBarToggleListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.onShowDuckChatInAddressBarToggled(isChecked)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setupToolbar(binding.includeToolbar.toolbar)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.showDuckAiInMenuToggle.apply {
            isVisible = viewState.shouldShowBrowserMenuToggle
            quietlySetIsChecked(viewState.showInBrowserMenu, menuToggleListener)
        }
        binding.showDuckAiInAddressBarToggle.apply {
            isVisible = viewState.shouldShowAddressBarToggle
            quietlySetIsChecked(viewState.showInAddressBar, addressBarToggleListener)
        }
    }
}
