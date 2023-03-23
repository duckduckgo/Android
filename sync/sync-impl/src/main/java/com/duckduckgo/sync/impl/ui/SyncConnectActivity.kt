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
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.databinding.ActivityConnectSyncBinding
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.LoginSucess
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ReadQRCode
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ReadTextCode
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ShowQRCode
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.ViewState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SyncConnectActivity : DuckDuckGoActivity() {
    private val binding: ActivityConnectSyncBinding by viewBinding()
    private val viewModel: SyncConnectViewModel by bindViewModel()

    // Register the launcher and result handler
    private val barcodeConnectLauncher = registerForActivityResult(
        ScanContract(),
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Read ${result.contents}", Toast.LENGTH_LONG).show()
            viewModel.onConnectQRScanned(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeUiEvents()
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
            ReadQRCode -> barcodeConnectLauncher.launch(getScanOptions())
            ReadTextCode -> TODO()
            LoginSucess -> {
                Toast.makeText(this, "Success", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
            }
            ShowQRCode -> ShowQRCodeActivity.intent(this).also { startActivity(it) }
            Command.Error -> {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun renderViewState(viewState: ViewState) {
        binding.readQRCode.setOnClickListener {
            viewModel.onReadQRCodeClicked()
        }
        binding.readTextCode.setOnClickListener {
            viewModel.onReadTextCodeClicked()
        }
        binding.showQRCode.setOnClickListener {
            viewModel.onShowQRCodeClicked()
        }
    }

    private fun getScanOptions(): ScanOptions {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setCameraId(0) // Use a specific camera of the device
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        return options
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, SyncConnectActivity::class.java)
        }
    }
}

