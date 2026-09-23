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

package com.duckduckgo.duckchat.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.internal.DuckAiUsageWarningsDevViewModel.Command
import com.duckduckgo.duckchat.internal.DuckAiUsageWarningsDevViewModel.ViewState
import com.duckduckgo.duckchat.internal.databinding.ActivityDuckAiUsageWarningsDevBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class DuckAiUsageWarningsDevActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityDuckAiUsageWarningsDevBinding by viewBinding()
    private val viewModel: DuckAiUsageWarningsDevViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()
        setupToolbar(binding.toolbar)
        title = getString(R.string.devSettingsDuckAiUsageWarningsTitle)

        binding.resetHighUsageDismissals.setOnClickListener { viewModel.onResetDismissalsClicked() }
        binding.resetUsageNoticeDismissal.setOnClickListener { viewModel.onResetUsageNoticeDismissalClicked() }

        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { renderView(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.appBar)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentLayout, drawBehindGestureNav = false)
    }

    private fun renderView(viewState: ViewState) {
        binding.highUsageDismissals.setSecondaryText(
            viewState.dismissedModelIds.takeIf { it.isNotEmpty() }?.sorted()?.joinToString()
                ?: getString(R.string.devSettingsDuckAiUsageWarningsNoDismissals),
        )
        binding.resetHighUsageDismissals.isEnabled = viewState.dismissedModelIds.isNotEmpty()
        binding.usageNoticeDismissal.setSecondaryText(
            viewState.usageNoticeDismissal?.let {
                getString(R.string.devSettingsDuckAiUsageWarningsUsageDismissalValue, it.noticeId.jsonId, it.window.jsonId, it.band)
            }
                ?: getString(R.string.devSettingsDuckAiUsageWarningsNoDismissals),
        )
        binding.resetUsageNoticeDismissal.isEnabled = viewState.usageNoticeDismissal != null
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.ShowMessage -> Toast.makeText(this, getString(command.messageResId), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, DuckAiUsageWarningsDevActivity::class.java)
    }
}
