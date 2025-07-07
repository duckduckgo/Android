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

package com.duckduckgo.app.dev.settings.tabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityDevTabsBinding
import com.duckduckgo.app.dev.settings.tabs.DevTabsViewModel.ViewState
import com.duckduckgo.app.notification.NotificationFactory
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class DevTabsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModel: DevTabsViewModel

    @Inject
    lateinit var factory: NotificationFactory

    private val binding: ActivityDevTabsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        binding.addTabsButton.setOnClickListener {
            viewModel.addTabs(binding.tabCount.text.toInt())
        }

        binding.clearTabsButton.setOnClickListener {
            viewModel.clearTabs()
        }

        binding.addBookmarksButton.setOnClickListener {
            viewModel.addBookmarks(binding.tabCount.text.toInt())
        }

        binding.clearBookmarksButton.setOnClickListener {
            viewModel.clearBookmarks()
        }

        observeViewState()
    }

    private fun observeViewState() {
        viewModel.viewState.flowWithLifecycle(lifecycle, STARTED).onEach { render(it) }
            .launchIn(lifecycleScope)
    }

    private fun render(viewState: ViewState) {
        binding.tabCountHeader.text = getString(R.string.devSettingsTabsScreenHeader, viewState.tabCount)
        binding.bookmarksCountHeader.text = getString(R.string.devSettingsTabsBookmarksScreenHeader, viewState.bookmarkCount)
    }

    companion object {

        fun intent(context: Context): Intent {
            return Intent(context, DevTabsActivity::class.java)
        }
    }
}
