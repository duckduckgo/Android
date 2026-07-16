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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2ThisDeviceBinding
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class SyncThisDeviceActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2ThisDeviceBinding>()

    private val viewModel by bindViewModel<SyncThisDeviceViewModel>()

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var syncSetupWideEvent: SyncSetupWideEvent

    private val launchSource get() = intent.getStringExtra(LAUNCH_SOURCE_EXTRA_KEY)

    private lateinit var progressDrawable: IndeterminateDrawable<CircularProgressIndicatorSpec>

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
        configureSyncWithAnotherCta()
        configureSyncWithThisCta()

        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.onScreenShown()
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.toolbar)
        edgeToEdgeHandler.applyNavigationBarInsetsAsMargin(binding.syncThisDeviceButton)
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: SyncThisDeviceViewModel.ViewState) {
        binding.syncThisDeviceButton.apply {
            icon = if (viewState.isSyncing) progressDrawable else null
            setText(if (viewState.isSyncing) R.string.sync_enable_connecting else R.string.sync_setup_v2_this_device_cta_title)
        }
    }

    private fun processCommand(command: SyncThisDeviceViewModel.Command) {
        when (command) {
            is SyncThisDeviceViewModel.Command.AbortSyncing -> {
                setResult(RESULT_CANCELED)
                finish()
            }

            is SyncThisDeviceViewModel.Command.FinishSyncing -> {
                setResult(RESULT_OK)
                finish()
            }

            is SyncThisDeviceViewModel.Command.ShowError -> {
                showError(command)
            }

            is SyncThisDeviceViewModel.Command.SyncWithAnotherDevice -> {
                setResult(RESULT_SYNC_WITH_ANOTHER_DEVICE)
                finish()
            }
        }
    }

    private fun configureToolbar() {
        binding.closeButton.setOnClickListener {
            viewModel.onCloseClicked()
        }
    }

    private fun configureSyncWithAnotherCta() {
        binding.syncWithAnotherDeviceButton.setOnClickListener {
            viewModel.onSyncWithAnotherDeviceClicked()
        }
    }

    private fun configureSyncWithThisCta() {
        val progressDrawableSpec = CircularProgressIndicatorSpec(this, null, 0).apply {
            indicatorSize = 16.toPx()
            indicatorInset = 0
            trackThickness = 2.toPx()
            indicatorColors = intArrayOf(getColorFromAttr(CommonR.attr.daxColorAccentBlue))
        }
        progressDrawable = IndeterminateDrawable.createCircularDrawable(this, progressDrawableSpec)

        binding.syncThisDeviceButton.setOnClickListener {
            if (!viewModel.viewState.value.isSyncing) {
                viewModel.syncThisDevice(launchSource)
            }
        }
    }

    private fun showError(error: SyncThisDeviceViewModel.Command.ShowError) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_error_title)
            .setMessage(getString(error.message) + "\n" + error.reason)
            .setPositiveButton(R.string.sync_dialog_error_ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onErrorDismissed()
                    }
                },
            )
            .show()
    }

    companion object {
        const val RESULT_SYNC_WITH_ANOTHER_DEVICE = 200

        private const val LAUNCH_SOURCE_EXTRA_KEY = "launch_source"

        fun intent(
            context: Context,
            source: String?,
        ): Intent {
            return Intent(context, SyncThisDeviceActivity::class.java).apply {
                putExtra(LAUNCH_SOURCE_EXTRA_KEY, source)
            }
        }
    }
}
