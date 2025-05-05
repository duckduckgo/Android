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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivityConnectSyncBinding
import com.duckduckgo.sync.impl.ui.EnterCodeActivity.Companion.Code.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.AskToSwitchAccount
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.FinishWithError
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.ReadTextCode
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.SwitchAccountSuccess
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.ViewState
import com.duckduckgo.sync.impl.ui.setup.EnterCodeContract
import com.duckduckgo.sync.impl.ui.setup.SyncSetupDeepLinkConnectedActivity
import com.duckduckgo.sync.impl.ui.setup.SyncSetupDeepLinkFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SyncWithAnotherDeviceActivity : DuckDuckGoActivity() {

    private val binding: ActivityConnectSyncBinding by viewBinding()
    private val viewModel: SyncWithAnotherActivityViewModel by bindViewModel()

    private var deepLinkSetupFragment: Fragment? = null

    private val enterCodeLauncher = registerForActivityResult(
        EnterCodeContract(),
    ) { result ->
        viewModel.onEnterCodeResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        extractDeepLinkCode()?.let {
            configureDeepLinkMode(it)
        }
        observeUiEvents()
        configureListeners()
    }

    private fun configureDeepLinkMode(deepLink: String) {
        // remove barcode reader; not needed when deep linking and don't want it prompting for camera permissions
        binding.readerContainer.removeView(binding.qrCodeReader)

        supportFragmentManager.commitNow {
            deepLinkSetupFragment = SyncSetupDeepLinkFragment.instance().also { fragment ->
                replace(R.id.fragment_container_view, fragment, FRAGMENT_TAG_DEVICE_CONNECTING)
            }
        }

        viewModel.onDeepLinkCodeReceived(deepLink)
    }

    override fun onResume() {
        super.onResume()
        binding.qrCodeReader.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.qrCodeReader.pause()
    }

    private fun observeUiEvents() {
        viewModel
            .viewState(canTimeout = isDeepLinkSetup())
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)
        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun render(viewState: ViewState) {
        viewState.qrCodeBitmap?.let {
            binding.qrCodeImageView.show()
            binding.qrCodeImageView.setImageBitmap(it)
            binding.copyCodeButton.setOnClickListener {
                viewModel.onCopyCodeClicked()
            }
        }
    }

    private fun processCommand(it: Command) {
        when (it) {
            ReadTextCode -> {
                enterCodeLauncher.launch(RECOVERY_CODE)
            }
            is LoginSuccess -> {
                setResult(RESULT_OK)
                finish()

                if (isDeepLinkSetup()) {
                    startActivity(SyncSetupDeepLinkConnectedActivity.intent(this))
                }
            }
            FinishWithError -> {
                setResult(RESULT_CANCELED)
                finish()
            }
            is ShowMessage -> Snackbar.make(binding.root, it.messageId, Snackbar.LENGTH_SHORT).show()
            is ShowError -> showError(it)
            is AskToSwitchAccount -> askUserToSwitchAccount(it)
            SwitchAccountSuccess -> {
                val resultIntent = Intent()
                resultIntent.putExtra(EXTRA_USER_SWITCHED_ACCOUNT, true)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun configureListeners() {
        binding.qrCodeReader.apply {
            // we don't want to initialise barcode scanner when deep linking
            if (!isDeepLinkSetup()) {
                decodeSingle { result -> viewModel.onQRCodeScanned(result) }
            }

            onCtaClicked {
                viewModel.onReadTextCodeClicked()
            }
        }
    }

    private fun extractDeepLinkCode(): String? {
        return intent.getStringExtra(EXTRA_DEEP_LINK_CODE)
    }

    private fun askUserToSwitchAccount(it: AskToSwitchAccount) {
        viewModel.onUserAskedToSwitchAccount()
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_switch_account_header)
            .setMessage(R.string.sync_dialog_switch_account_description)
            .setPositiveButton(R.string.sync_dialog_switch_account_primary_button)
            .setNegativeButton(R.string.sync_dialog_switch_account_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onUserAcceptedJoiningNewAccount(it.encodedStringCode)
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onUserCancelledJoiningNewAccount()
                        if (isDeepLinkSetup()) {
                            finish()
                        }
                    }
                },
            ).show()
    }

    private fun showError(it: ShowError) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_error_title)
            .setMessage(getString(it.message) + "\n" + it.reason)
            .setPositiveButton(R.string.sync_dialog_error_ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onErrorDialogDismissed()
                    }
                },
            ).show()
    }

    companion object {
        const val EXTRA_USER_SWITCHED_ACCOUNT = "userSwitchedAccount"
        private const val EXTRA_DEEP_LINK_CODE = "deepLinkCode"
        private const val FRAGMENT_TAG_DEVICE_CONNECTING = "device-connecting"

        internal fun intent(context: Context): Intent {
            return Intent(context, SyncWithAnotherDeviceActivity::class.java)
        }

        internal fun intentForDeepLink(context: Context, syncBarcodeUrl: String): Intent {
            return Intent(context, SyncWithAnotherDeviceActivity::class.java).apply {
                putExtra(EXTRA_DEEP_LINK_CODE, syncBarcodeUrl)
            }
        }
    }

    private fun isDeepLinkSetup() = deepLinkSetupFragment != null
}
