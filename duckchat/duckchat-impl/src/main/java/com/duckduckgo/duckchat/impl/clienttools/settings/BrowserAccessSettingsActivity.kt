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

package com.duckduckgo.duckchat.impl.clienttools.settings

import android.os.Bundle
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.RadioListOption
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.clienttools.settings.BrowserAccessSettingsViewModel.Command
import com.duckduckgo.duckchat.impl.clienttools.settings.BrowserAccessSettingsViewModel.ToolRow
import com.duckduckgo.duckchat.impl.clienttools.settings.BrowserAccessSettingsViewModel.ViewState
import com.duckduckgo.duckchat.impl.databinding.ActivityDuckAiBrowserAccessSettingsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class BrowserAccessSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: BrowserAccessSettingsViewModel by bindViewModel()
    private val binding: ActivityDuckAiBrowserAccessSettingsBinding by viewBinding()

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = true)

        binding.resetAll.setClickListener { viewModel.onResetAllClicked() }

        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)
        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { handle(it) }
            .launchIn(lifecycleScope)
    }

    private fun render(viewState: ViewState) {
        binding.seeSectionHeader.isVisible = viewState.seeTools.isNotEmpty()
        renderRows(binding.seeTools, viewState.seeTools)
        binding.doSectionDivider.isVisible = viewState.doTools.isNotEmpty()
        binding.doSectionHeader.isVisible = viewState.doTools.isNotEmpty()
        renderRows(binding.doTools, viewState.doTools)
        binding.resetAll.isEnabled = viewState.isResetAllEnabled
    }

    private fun renderRows(
        container: LinearLayout,
        rows: List<ToolRow>,
    ) {
        container.removeAllViews()
        rows.forEach { row ->
            val item = TwoLineListItem(this).apply {
                setLeadingIconResource(row.icon)
                setPrimaryText(getString(row.name))
                setSecondaryText(getString(row.choice.label))
                setClickListener { viewModel.onToolClicked(row.toolName) }
            }
            container.addView(item, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.ShowChoices -> showChoices(command.row)
            Command.ConfirmResetAll -> confirmResetAll()
        }
    }

    private fun showChoices(row: ToolRow) {
        RadioListAlertDialogBuilder(this)
            .setTitle(row.name)
            .setMessage(row.description)
            .setOptions(row.options.map { RadioListOption(it.label, isSelected = it == row.choice) })
            .setPositiveButton(CommonR.string.dialogSave)
            .setNegativeButton(CommonR.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        row.options.getOrNull(selectedItem - 1)?.let { viewModel.onChoiceSelected(row.toolName, it) }
                    }
                },
            )
            .show()
    }

    private fun confirmResetAll() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.duckAiBrowserAccessResetAllDialogTitle)
            .setMessage(R.string.duckAiBrowserAccessResetAllDialogMessage)
            .setPositiveButton(R.string.duckAiBrowserAccessResetAllDialogConfirm, DESTRUCTIVE)
            .setNegativeButton(CommonR.string.cancel, GHOST_ALT)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onResetAllConfirmed()
                    }
                },
            )
            .show()
    }
}
