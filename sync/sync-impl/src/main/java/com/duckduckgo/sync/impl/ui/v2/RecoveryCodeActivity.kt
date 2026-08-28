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
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
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
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var shareAction: ShareAction

    private val autoRestoreListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.changeRestoreOnReinstall(isChecked)
    }

    private val downloadPdfPermissionLauncher = registerForActivityResult(
        RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            lifecycleScope.launch { viewModel.generateRecoveryCodeSheet(this@RecoveryCodeActivity) }
        } else {
            viewModel.showMessage(R.string.sync_simplified_recovery_code_storage_permission_message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()

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
        val hasRecoveryCode = viewState.recoveryCode != null

        binding.recoveryCodeContainer.isInvisible = !hasRecoveryCode
        binding.recoveryCodeView.setRecoveryCode(viewState.recoveryCode)
        binding.restoreOnReinstallToggle.apply {
            isVisible = viewState.isAutoRestoreAvailable
            quietlySetIsChecked(viewState.isAutoRestoreEnabled, autoRestoreListener)
        }
        binding.loadingShimmer.apply {
            isGone = hasRecoveryCode
            if (hasRecoveryCode) stopShimmer() else startShimmer()
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
        val deviceName = requireNotNull(intent.getStringExtra(DEVICE_NAME_EXTRA_KEY)) {
            "Missing intent extra: '$DEVICE_NAME_EXTRA_KEY'"
        }
        binding.headlineText.text = getString(R.string.sync_simplified_recovery_code_headline, deviceName)
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
            .setTitle(R.string.sync_simplified_error_dialog_title)
            .setMessage(getString(showError.message) + "\n" + showError.reason)
            .setPositiveButton(R.string.sync_simplified_error_dialog_primary_button)
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
        // Deliberately committed only when leaving the screen: SyncActivity re-reads this preference
        // from storage when it returns to the foreground, so it must be written before this activity
        // finishes, but intermediate toggle flips shouldn't cause repeated Block Store writes.
        viewModel.persistRecoveryPayload()

        if (viewModel.viewState.value.recoveryCode != null) {
            setResult(RESULT_OK)
        } else {
            setResult(RESULT_CANCELED)
        }

        super.finish()
    }

    companion object {
        private const val DEVICE_NAME_EXTRA_KEY = "device_name"

        fun intent(
            context: Context,
            deviceName: String,
        ): Intent {
            return Intent(context, RecoveryCodeActivity::class.java).putExtra(DEVICE_NAME_EXTRA_KEY, deviceName)
        }
    }
}
