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

package com.duckduckgo.sync.impl.ui.auditlog

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncAuditLogBinding
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

object SyncAuditLogScreenParams : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SyncAuditLogScreenParams::class)
class SyncAuditLogActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivitySyncAuditLogBinding by viewBinding()
    private val viewModel: SyncAuditLogViewModel by bindViewModel()
    private val adapter = SyncAuditLogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        configureEdgeToEdgeInsets()
        binding.syncAuditLogList.adapter = adapter

        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.syncAuditLogList)
    }

    private fun render(state: ViewState) {
        binding.loadingIndicator.isVisible = state is ViewState.Loading
        binding.syncAuditLogList.isVisible = state is ViewState.Loaded
        binding.messageText.isVisible = state is ViewState.Empty || state is ViewState.Error
        when (state) {
            ViewState.Empty -> binding.messageText.setText(R.string.syncAuditLogEmpty)
            ViewState.Error -> binding.messageText.setText(R.string.syncAuditLogError)
            is ViewState.Loaded -> adapter.submitRows(state.rows)
            ViewState.Loading -> Unit
        }
    }
}
