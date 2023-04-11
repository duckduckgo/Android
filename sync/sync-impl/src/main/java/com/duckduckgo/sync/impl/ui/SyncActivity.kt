/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui

import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo.Builder
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncBinding
import com.duckduckgo.sync.impl.databinding.ViewRecoveryCodeBinding
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchDeviceSetupFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.StoreRecoveryCodePDF
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.ViewState
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SyncActivityWithEmptyParams::class)
class SyncActivity : DuckDuckGoActivity() {
    private val binding: ActivitySyncBinding by viewBinding()
    private val viewModel: SyncActivityViewModel by bindViewModel()

    private val deviceSyncStatusToggleListener: OnCheckedChangeListener = object : OnCheckedChangeListener {
        override fun onCheckedChanged(
            buttonView: CompoundButton?,
            isChecked: Boolean,
        ) {
            viewModel.onToggleClicked(isChecked)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeUiEvents()
    }

    override fun onStart() {
        super.onStart()
        viewModel.getSyncState()
    }

    private fun observeUiEvents() {
        viewModel
            .viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            LaunchDeviceSetupFlow -> {
                startActivity(SetupAccountActivity.intentStartSetupFlow(this))
            }

            AskTurnOffSync -> askTurnOffsync()
            AskDeleteAccount -> askDeleteAccount()

            is StoreRecoveryCodePDF -> {
                layoutAsPdfTest(it)
            }
        }
    }

    private fun screenAsPdfTest() {
        PdfDocument().apply {
            val page = startPage(Builder(2250, 1400, 1).create())
            binding.root.draw(page.canvas)
            finishPage(page)
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloads, "recovery-code.pdf")
            writeTo(FileOutputStream(file))
            close()
        }
    }

    private fun layoutAsPdfTest(command: StoreRecoveryCodePDF) {
        PdfDocument().apply {
            val page = startPage(Builder(1500, 2115, 1).create())

            ViewRecoveryCodeBinding.inflate(layoutInflater, null, false).apply {
                this.qrCodeImageView.setImageBitmap(command.recoveryCodeB64)
                this.recoveryCodeText.text = command.recoveryCode
                val measureWidth: Int = View.MeasureSpec.makeMeasureSpec(page.canvas.width, View.MeasureSpec.EXACTLY)
                val measuredHeight: Int = View.MeasureSpec.makeMeasureSpec(page.canvas.height, View.MeasureSpec.EXACTLY)
                this.root.measure(measureWidth, measuredHeight)
                this.root.layout(0, 0, page.canvas.width, page.canvas.height)
                this.root.draw(page.canvas)
            }
            finishPage(page)
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloads, "recovery-code.pdf")
            writeTo(FileOutputStream(file))
            close()
        }
    }

    private fun askDeleteAccount() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.turn_off_sync_dialog_title)
            .setMessage(getString(R.string.turn_off_sync_dialog_content))
            .setPositiveButton(R.string.turn_off_sync_dialog_primary_button)
            .setNegativeButton(R.string.turn_off_sync_dialog_secondary_button)
            .setDestructiveButtons(true)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onDeleteAccountConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onDeleteAccountCancelled()
                    }
                },
            ).show()
    }

    private fun askTurnOffsync() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.turn_off_sync_dialog_title)
            .setMessage(getString(R.string.turn_off_sync_dialog_content))
            .setPositiveButton(R.string.turn_off_sync_dialog_primary_button)
            .setNegativeButton(R.string.turn_off_sync_dialog_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onTurnOffSyncConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onTurnOffSyncCancelled()
                    }
                },
            ).show()
    }

    private fun renderViewState(viewState: ViewState) {
        Timber.i("CRIS: renderViewState: $viewState")
        binding.deviceSyncStatusToggle.quietlySetIsChecked(viewState.isDeviceSyncEnabled, deviceSyncStatusToggleListener)
        binding.viewSwitcher.displayedChild = if (viewState.showAccount) 1 else 0

        if (viewState.showAccount) {
            if (viewState.loginQRCode != null) {
                binding.qrCodeImageView.show()
                binding.qrCodeImageView.setImageBitmap(viewState.loginQRCode)
            }

            binding.saveRecoveryCodeItem.setOnClickListener {
                viewModel.onSaveRecoveryCodeClicked()
            }

            binding.deleteAccountButton.setOnClickListener {
                viewModel.onDeleteAccountClicked()
            }
        }
    }
}
