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

package com.duckduckgo.autoconsent.impl.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.autoconsent.impl.databinding.ActivityAutoconsentSettingsBinding
import com.duckduckgo.autoconsent.impl.ui.AutoconsentSettingsViewModel.ViewState
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class AutoconsentSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityAutoconsentSettingsBinding by viewBinding()

    private val viewModel: AutoconsentSettingsViewModel by bindViewModel()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val autoconsentToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onUserToggleAutoconsent(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)
        configureUiEventHandlers()
        observeViewModel()
    }

    private fun configureUiEventHandlers() {
        binding.autoconsentToggle.setOnCheckedChangeListener(autoconsentToggleListener)
    }

    private fun observeViewModel() {
        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }
            .launchIn(lifecycleScope)
    }

    private fun render(viewState: ViewState) {
        binding.autoconsentToggle.quietlySetIsChecked(viewState.autoconsentEnabled, autoconsentToggleListener)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AutoconsentSettingsActivity::class.java)
        }
    }
}
