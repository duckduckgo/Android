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

package com.duckduckgo.sync.impl.ui.v2

import android.os.Bundle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2Binding
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class SyncActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2Binding>()

    private val viewModel by bindViewModel<SyncActivityViewModel>()

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isEdgeToEdge = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SYNC)
        if (isEdgeToEdge) {
            enableTransparentEdgeToEdge()
        }
        setContentView(binding.root)
        if (isEdgeToEdge) {
            configureEdgeToEdgeInsets()
        }

        configureToolbar()

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.syncHeader.setState(
            isSyncEnabled = viewState.showAccount,
            isDuckAiAvailable = viewState.aiChatSyncEnabled,
        )
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = true)
    }

    private fun configureToolbar() {
        setSupportActionBar(binding.includeToolbar.toolbar)
        binding.includeToolbar.toolbar.setNavigationIcon(CommonR.drawable.ic_arrow_left_24)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}
