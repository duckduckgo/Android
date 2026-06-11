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

package com.duckduckgo.downloads.impl.location

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.DownloadsScreens.DownloadLocationSettingsScreenNoParams
import com.duckduckgo.downloads.impl.R
import com.duckduckgo.downloads.impl.databinding.ActivityDownloadLocationSettingsBinding
import com.duckduckgo.downloads.impl.location.DownloadLocationSettingsViewModel.Command.LaunchFolderPicker
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DownloadLocationSettingsScreenNoParams::class)
class DownloadLocationSettingsActivity : DuckDuckGoActivity() {

    private val viewModel: DownloadLocationSettingsViewModel by bindViewModel()
    private val binding: ActivityDownloadLocationSettingsBinding by viewBinding()

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val treeUri = data?.data ?: return@registerForActivityResult
        val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        viewModel.onFolderSelected(treeUri, takeFlags)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        supportActionBar?.setTitle(R.string.downloadsLocationActivityTitle)

        binding.changeFolderSetting.setClickListener { viewModel.onChangeFolderClicked() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                binding.downloadLocationSetting.setSecondaryText(getString(state.locationSubtitleRes))
                binding.selectedFolderSetting.setSecondaryText(
                    state.selectedFolderLabel ?: getString(state.selectedFolderFallbackRes),
                )
                binding.downloadLocationUnavailableMessage.isVisible = state.showUnavailableMessage
            }
            .launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { command ->
                when (command) {
                    LaunchFolderPicker -> launchFolderPicker()
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun launchFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildTreeDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download",
                ),
            )
        }
        folderPickerLauncher.launch(intent)
    }
}
