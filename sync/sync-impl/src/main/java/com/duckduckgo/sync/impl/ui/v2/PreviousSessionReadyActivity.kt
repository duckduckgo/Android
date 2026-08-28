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
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2PreviousSessionReadyBinding
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.toAutoRestorePixelSource
import com.duckduckgo.sync.impl.ui.v2.PreviousSessionReadyViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.PreviousSessionReadyViewModel.Factory
import com.duckduckgo.sync.impl.ui.v2.PreviousSessionReadyViewModel.Factory.Provider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class PreviousSessionReadyActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2PreviousSessionReadyBinding>()

    @Inject
    lateinit var vmFactory: Factory

    private val viewModel by viewModels<PreviousSessionReadyViewModel> {
        val launchSource = syncEntryPoint.toAutoRestorePixelSource()
        Provider(vmFactory, launchSource)
    }

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val syncEntryPoint
        get() = requireNotNull(IntentCompat.getSerializableExtra(intent, ORIGINAL_FLOW_EXTRA_KEY, SyncEntryPoint::class.java)) {
            "Missing intent extra: '$ORIGINAL_FLOW_EXTRA_KEY'"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()

        configureToolbar()
        configureButtons()
        configureBackNavigation()

        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.onScreenShown()
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.toolbar)
        edgeToEdgeHandler.applyNavigationBarInsetsAsMargin(binding.setUpNewSyncButton)
    }

    private fun configureToolbar() {
        binding.closeButton.setOnClickListener {
            viewModel.onCloseClicked()
        }
    }

    private fun configureButtons() {
        binding.resumeButton.setOnClickListener {
            viewModel.onResumeClicked()
        }
        binding.setUpNewSyncButton.setOnClickListener {
            viewModel.onContinueSetupClicked()
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            viewModel.onCloseClicked()
        }
    }

    private fun observeViewModel() {
        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.SetResumeResult -> {
                setResult(
                    PreviousSessionReadyContract.RESULT_RESUME,
                    PreviousSessionReadyContract.resumeResultIntent(command.recoveryCode),
                )
            }

            is Command.SetContinueSetupResult -> {
                setResult(
                    PreviousSessionReadyContract.RESULT_CONTINUE_SETUP,
                    PreviousSessionReadyContract.continueSetupResultIntent(syncEntryPoint),
                )
            }

            is Command.Close -> finish()

            is Command.ShowError -> showError(command)
        }
    }

    private fun showError(error: Command.ShowError) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_error_dialog_title)
            .setMessage(error.message)
            .setPositiveButton(R.string.sync_simplified_error_dialog_primary_button)
            .show()
    }

    companion object {
        private const val ORIGINAL_FLOW_EXTRA_KEY = "original_flow"

        fun intent(
            context: Context,
            syncEntryPoint: SyncEntryPoint,
        ): Intent {
            return Intent(context, PreviousSessionReadyActivity::class.java).apply {
                putExtra(ORIGINAL_FLOW_EXTRA_KEY, syncEntryPoint)
            }
        }
    }
}
