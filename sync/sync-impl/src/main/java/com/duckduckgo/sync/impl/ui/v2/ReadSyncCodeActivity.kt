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
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.content.IntentCompat
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2ReadSyncCodeBinding
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeViewModel.Command.StartSyncProcess
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class ReadSyncCodeActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2ReadSyncCodeBinding>()

    private val viewModel by bindViewModel<ReadSyncCodeViewModel>()

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val launchSource get() = intent.getStringExtra(LAUNCH_SOURCE_EXTRA_KEY)

    private val syncEntryPoint
        get() = requireNotNull(IntentCompat.getSerializableExtra(intent, ORIGINAL_FLOW_EXTRA_KEY, SyncEntryPoint::class.java)) {
            "Missing intent extra: '$ORIGINAL_FLOW_EXTRA_KEY'"
        }

    private val isRecoveryFlow get() = syncEntryPoint == SyncEntryPoint.RECOVER_SYNCED_DATA

    private val showQrCodeLauncher = registerForActivityResult(
        DisplayQrCodeContract(),
    ) { output ->
        when (output) {
            is DisplayQrCodeContract.Output.SyncCompleted -> finishWithResult(output.result)
            is DisplayQrCodeContract.Output.Dismissed -> Unit
        }
    }

    private val exchangeSyncCodeLauncher = registerForActivityResult(
        ExchangeSyncCodeContract(),
    ) { output ->
        when (output) {
            is ExchangeSyncCodeContract.Output.SyncCompleted -> finishWithResult(output.result)
            is ExchangeSyncCodeContract.Output.Dismissed -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SYNC)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }
        setContentView(binding.root)
        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

        configureToolbar()
        configureContentAdapter()
        configureContentCorners()

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel
            .commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is StartSyncProcess -> {
                exchangeSyncCodeLauncher.launch(
                    ExchangeSyncCodeContract.Input(
                        syncCode = command.syncCode,
                        launchSource = launchSource,
                        syncEntryPoint = syncEntryPoint,
                    ),
                )
            }

            is ShowMessage -> {
                Snackbar.make(binding.root, command.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun finishWithResult(result: SyncPairingResult) {
        setResult(SyncPairingResult.RESULT_SYNC_COMPLETED, SyncPairingResult.resultIntent(result))
        finish()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.toolbar)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentPager, drawBehindGestureNav = false)
    }

    private fun configureToolbar() {
        binding.closeButton.setOnClickListener {
            finish()
        }
        binding.showQrCodeButton.isGone = isRecoveryFlow
        binding.showQrCodeButton.setOnClickListener {
            showQrCodeLauncher.launch(
                DisplayQrCodeContract.Input(
                    launchSource = launchSource,
                    syncEntryPoint = syncEntryPoint,
                ),
            )
        }
    }

    private fun configureContentAdapter() {
        binding.contentPager.isUserInputEnabled = false
        binding.contentPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    SCANNER_POSITION -> ReadSyncCodeCameraFragment()
                    MANUAL_CODE_ENTRY_POSITION -> ReadSyncCodeManualFragment()
                    else -> error("Unknown position: $position")
                }
            }
        }
        val mediator = TabLayoutMediator(binding.tabContainer, binding.contentPager) { tab, position ->
            tab.text = when (position) {
                SCANNER_POSITION -> getString(R.string.sync_simplified_scanner_camera_tab_label)
                MANUAL_CODE_ENTRY_POSITION -> getString(R.string.sync_simplified_scanner_manual_entry_tab_label)
                else -> error("Unknown position: $position")
            }
        }
        mediator.attach()
    }

    private fun configureContentCorners() {
        val radius = 28f.toPx()
        binding.contentPager.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) {
                // Extend the rect past the bottom so only the top corners are rounded
                outline.setRoundRect(0, 0, view.width, view.height + radius.toInt(), radius)
            }
        }
        binding.contentPager.clipToOutline = true
    }

    companion object {
        private const val SCANNER_POSITION = 0
        private const val MANUAL_CODE_ENTRY_POSITION = 1
        private const val LAUNCH_SOURCE_EXTRA_KEY = "launch_source"
        private const val ORIGINAL_FLOW_EXTRA_KEY = "original_flow"

        fun intent(
            context: Context,
            launchSource: String?,
            syncEntryPoint: SyncEntryPoint,
        ): Intent {
            return Intent(context, ReadSyncCodeActivity::class.java).apply {
                putExtra(LAUNCH_SOURCE_EXTRA_KEY, launchSource)
                putExtra(ORIGINAL_FLOW_EXTRA_KEY, syncEntryPoint)
            }
        }
    }
}
