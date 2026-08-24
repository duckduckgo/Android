/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.icon.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.GridLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityAppIconsBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class ChangeIconActivity : DuckDuckGoActivity() {

    private val binding: ActivityAppIconsBinding by viewBinding()
    private val viewModel: ChangeIconViewModel by bindViewModel()
    private val metrics by lazy { AppIconCellMetrics(resources) }
    private val iconsAdapter: AppIconsAdapter by lazy {
        AppIconsAdapter(metrics) { icon ->
            viewModel.onIconSelected(icon)
        }
    }

    private val toolbar
        get() = binding.includeToolbar.toolbar

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        setupToolbar(toolbar)
        configureEdgeToEdgeInsets()
        configureRecycler()

        observeViewModel()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.appIconsList, drawBehindGestureNav = true)
    }

    private fun configureRecycler() {
        val horizontalPadding = binding.appIconsList.paddingStart + binding.appIconsList.paddingEnd
        val layoutManager = GridLayoutManager(this, spanCountFor(resources.displayMetrics.widthPixels - horizontalPadding))
        binding.appIconsList.layoutManager = layoutManager
        binding.appIconsList.addItemDecoration(AppIconSpacingDecoration(metrics))
        binding.appIconsList.adapter = iconsAdapter
        binding.appIconsList.doOnLayout {
            layoutManager.spanCount = spanCountFor(it.width - it.paddingStart - it.paddingEnd)
        }
    }

    /** Fits as many columns as the width allows while keeping at least the design's gap between icons. */
    private fun spanCountFor(availableWidth: Int): Int = ((availableWidth + metrics.cellGap) / (metrics.cellSize + metrics.cellGap)).coerceAtLeast(1)

    private fun observeViewModel() {
        viewModel.viewState.observe(this) { viewState ->
            viewState?.let {
                render(it)
            }
        }

        viewModel.command.observe(this) {
            processCommand(it)
        }

        viewModel.start()
    }

    private fun render(viewState: ChangeIconViewModel.ViewState) {
        iconsAdapter.notifyChanges(viewState.appIcons)
    }

    private fun processCommand(it: ChangeIconViewModel.Command) {
        when (it) {
            is ChangeIconViewModel.Command.IconChanged -> {
                finish()
            }

            is ChangeIconViewModel.Command.ShowConfirmationDialog -> {
                TextAlertDialogBuilder(this)
                    .setTitle(R.string.changeIconDialogTitle)
                    .setMessage(getString(R.string.changeIconDialogMessage))
                    .setPositiveButton(R.string.changeIconCtaAccept)
                    .setNegativeButton(R.string.changeIconCtaCancel)
                    .addEventListener(
                        object : TextAlertDialogBuilder.EventListener() {
                            override fun onPositiveButtonClicked() {
                                viewModel.onIconConfirmed(it.viewData)
                            }
                        },
                    )
                    .show()
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, ChangeIconActivity::class.java)
        }
    }
}
