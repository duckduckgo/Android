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

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.CustomDownloadLocation
import com.duckduckgo.downloads.api.DownloadLocationRepository
import com.duckduckgo.downloads.impl.R
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DownloadLocationSettingsViewModel @Inject constructor(
    private val downloadLocationRepository: DownloadLocationRepository,
    private val safDownloadStorage: SafDownloadStorage,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        @StringRes val locationSubtitleRes: Int = R.string.downloadsLocationDefaultSubtitle,
        val selectedFolderLabel: String? = null,
        @StringRes val selectedFolderFallbackRes: Int = R.string.downloadsLocationDefaultFolderName,
        val showUnavailableMessage: Boolean = false,
    )

    sealed class Command {
        data object LaunchFolderPicker : Command()
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    init {
        downloadLocationRepository.customLocationFlow
            .onEach { refreshFromStoredLocation(it) }
            .launchIn(viewModelScope)
    }

    fun commands() = command.receiveAsFlow()

    fun onChangeFolderClicked() {
        viewModelScope.launch {
            command.send(Command.LaunchFolderPicker)
        }
    }

    fun onFolderSelected(treeUri: Uri, takeFlags: Int) {
        val success = runCatching {
            safDownloadStorage.takePersistableTreePermission(treeUri, takeFlags)
        }.isSuccess
        if (!success) {
            logcat { "Failed to take persistable tree permission for URI: $treeUri" }
            return
        }
        viewModelScope.launch(dispatcherProvider.io()) {
            val displayName = safDownloadStorage.getTreeDisplayName(treeUri).orEmpty()
            val pathLabel = safDownloadStorage.buildPathLabel(treeUri)
            downloadLocationRepository.saveCustomLocation(
                CustomDownloadLocation(
                    treeUri = treeUri.toString(),
                    displayName = displayName,
                    pathLabel = pathLabel,
                ),
            )
            refreshFromStoredLocation(downloadLocationRepository.getCustomLocation())
        }
    }

    private suspend fun refreshFromStoredLocation(location: CustomDownloadLocation?) {
        if (location == null) {
            _viewState.update {
                it.copy(
                    locationSubtitleRes = R.string.downloadsLocationDefaultSubtitle,
                    selectedFolderLabel = null,
                    showUnavailableMessage = false,
                )
            }
            return
        }

        val isAccessible = withContext(dispatcherProvider.io()) {
            safDownloadStorage.isTreeAccessible(Uri.parse(location.treeUri))
        }
        _viewState.update {
            it.copy(
                locationSubtitleRes = R.string.downloadsLocationCustomSubtitle,
                selectedFolderLabel = location.pathLabel,
                showUnavailableMessage = !isAccessible,
            )
        }
    }
}
