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

package com.duckduckgo.sync.impl.ui.pairing.recover

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.auth.AuthPromptRenderer
import com.duckduckgo.sync.impl.databinding.ActivityRecoverSyncedDataBinding
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.pairing.SyncPairingResult
import com.duckduckgo.sync.impl.ui.pairing.read.ReadSyncCodeContract
import com.duckduckgo.sync.impl.ui.pairing.recover.RecoverSyncedDataViewModel.Command
import com.duckduckgo.sync.impl.ui.pairing.recover.RecoverSyncedDataViewModel.Factory.Provider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class RecoverSyncedDataActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivityRecoverSyncedDataBinding>()

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var authPromptRenderer: AuthPromptRenderer

    @Inject
    lateinit var vmFactory: RecoverSyncedDataViewModel.Factory

    private val launchSource get() = intent.getStringExtra(LAUNCH_SOURCE_EXTRA_KEY)

    private val isAuthRequired get() = intent.getBooleanExtra(IS_AUTH_REQUIRED_EXTRA_KEY, true)

    private val viewModel by viewModels<RecoverSyncedDataViewModel> {
        Provider(vmFactory, isAuthRequired)
    }

    private val readSyncCodeLauncher = registerForActivityResult(
        ReadSyncCodeContract(),
    ) { output ->
        when (output) {
            is ReadSyncCodeContract.Output.SyncCompleted -> {
                setResult(SyncPairingResult.RESULT_SYNC_COMPLETED, SyncPairingResult.resultIntent(output.result))
                finish()
            }

            is ReadSyncCodeContract.Output.Dismissed -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()

        configureToolbar()
        configureRecoverDataCta()

        observeViewModel()
        authPromptRenderer.bind(viewModel.authPrompts)
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.toolbar)
        edgeToEdgeHandler.applyNavigationBarInsetsAsMargin(binding.recoverDataButton)
    }

    private fun configureToolbar() {
        binding.closeButton.setOnClickListener {
            finish()
        }
    }

    private fun configureRecoverDataCta() {
        binding.recoverDataButton.setOnClickListener { viewModel.onRecoverDataClicked() }
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
            is Command.ReadSyncCode -> {
                readSyncCodeLauncher.launch(
                    ReadSyncCodeContract.Input(
                        syncEntryPoint = SyncEntryPoint.RECOVER_SYNCED_DATA,
                        launchSource = launchSource,
                    ),
                )
            }

            is Command.ShowAuthError -> {
                Snackbar.make(binding.root, R.string.sync_simplified_error_dialog_generic_body, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val LAUNCH_SOURCE_EXTRA_KEY = "launch_source"
        private const val IS_AUTH_REQUIRED_EXTRA_KEY = "is_auth_required"

        fun intent(
            context: Context,
            launchSource: String?,
            isAuthRequired: Boolean,
        ): Intent {
            return Intent(context, RecoverSyncedDataActivity::class.java).apply {
                putExtra(LAUNCH_SOURCE_EXTRA_KEY, launchSource)
                putExtra(IS_AUTH_REQUIRED_EXTRA_KEY, isAuthRequired)
            }
        }
    }
}
