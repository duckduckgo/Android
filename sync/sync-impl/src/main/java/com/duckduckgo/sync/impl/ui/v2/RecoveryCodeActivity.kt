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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2RecoveryCodesBinding
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.CheckStoragePermission
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.ShareRecoveryCodeFile
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.ViewState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class RecoveryCodeActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2RecoveryCodesBinding>()

    private val viewModel by bindViewModel<RecoveryCodeActivityViewModel>()

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var shareAction: ShareAction

    private val autoRestoreListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.changeRestoreOnReinstall(isChecked)
    }

    private val downloadPdfPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted ->
        if (isGranted) {
            lifecycleScope.launch { viewModel.generateRecoveryCodeSheet(this@RecoveryCodeActivity) }
        } else {
            viewModel.showMessage(R.string.sync_permission_required_store_recovery_code)
        }
    }

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

        configureHeadline()
        configureCopyCodeItem()
        configureDownloadCodeButton()
        configureRestoreOnReinstallToggle()
        configureDoneButton()

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.recoveryCodeContainer.setRecoveryCode(viewState.recoveryCode)
        binding.restoreOnReinstallToggle.apply {
            isVisible = viewState.isAutoRestoreAvailable
            quietlySetIsChecked(viewState.isAutoRestoreEnabled, autoRestoreListener)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is CheckStoragePermission -> {
                if (appBuildConfig.sdkInt < 30) {
                    downloadPdfPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    lifecycleScope.launch { viewModel.generateRecoveryCodeSheet(this@RecoveryCodeActivity) }
                }
            }

            is ShareRecoveryCodeFile -> {
                shareAction.shareFile(this, command.pdfFile)
            }

            is ShowMessage -> {
                Snackbar.make(binding.root, command.message, Snackbar.LENGTH_LONG).show()
            }

            is ShowError -> {
                showError(command)
            }

            is Close -> {
                finish()
            }
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.contentScrollView)
        edgeToEdgeHandler.applyNavigationBarInsetsAsMargin(binding.doneButton)
    }

    private fun configureHeadline() {
        val deviceName = requireNotNull(intent.getStringExtra(DEVICE_NAME_KEY)) {
            "Missing intent extra: '$DEVICE_NAME_KEY'"
        }
        binding.headlineText.text = getString(R.string.sync_device_v2_recovery_code_headline, deviceName)
    }

    private fun configureCopyCodeItem() {
        binding.recoveryCodeContainer.setOnClickListener { viewModel.onCopyCodeClicked() }
    }

    private fun configureDownloadCodeButton() {
        binding.downloadCodeButton.setOnClickListener { viewModel.onDownloadCodeClicked() }
    }

    private fun configureRestoreOnReinstallToggle() {
        binding.restoreOnReinstallToggle.setOnCheckedChangeListener(autoRestoreListener)
    }

    private fun configureDoneButton() {
        binding.doneButton.setOnClickListener { viewModel.onDoneClicked() }
    }

    private fun showError(showError: ShowError) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_error_title)
            .setMessage(getString(showError.message) + "\n" + showError.reason)
            .setPositiveButton(R.string.sync_dialog_error_ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onErrorDialogDismissed()
                    }
                },
            )
            .show()
    }

    override fun finish() {
        val state = viewModel.viewState.value
        if (state.recoveryCode == null) {
            setResult(RESULT_CANCELED)
        } else {
            val useAutoRestore = state.isAutoRestoreAvailable && state.isAutoRestoreEnabled
            setResult(RESULT_OK, RecoveryCodeContract.resultIntent(useAutoRestore))
        }
        super.finish()
    }

    companion object {
        private const val DEVICE_NAME_KEY = "device_name"

        fun intent(
            context: Context,
            deviceName: String,
        ): Intent {
            return Intent(context, RecoveryCodeActivity::class.java).putExtra(DEVICE_NAME_KEY, deviceName)
        }
    }
}
