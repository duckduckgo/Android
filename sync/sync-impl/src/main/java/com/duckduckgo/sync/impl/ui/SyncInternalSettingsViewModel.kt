/*
 * Copyright (c) 2022 DuckDuckGo
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.sync.api.favicons.FaviconsFetchingStore
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.getOrNull
import com.duckduckgo.sync.impl.internal.SyncInternalEnvDataStore
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ReadConnectQR
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ReadQR
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ShowQR
import com.duckduckgo.sync.store.*
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class SyncInternalSettingsViewModel
@Inject
constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val savedSitesRepository: SavedSitesRepository,
    private val autofillStore: AutofillStore,
    private val syncStore: SyncStore,
    private val syncEnvDataStore: SyncInternalEnvDataStore,
    private val syncFaviconFetchingStore: FaviconsFetchingStore,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { getConnectedDevices() }
    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val userId: String = "",
        val deviceName: String = "",
        val deviceId: String = "",
        val token: String = "",
        val isSignedIn: Boolean = false,
        val primaryKey: String = "",
        val secretKey: String = "",
        val protectedEncryptionKey: String = "",
        val passwordHash: String = "",
        val connectedDevices: List<ConnectedDevice> = emptyList(),
        val useDevEnvironment: Boolean = false,
        val environment: String = "",
        val e2eTests: E2ETest? = null,
    )

    data class E2ETest(
        val assertResult: Boolean,
        val errors: String,
    )

    sealed class Command {
        data class ShowMessage(val message: String) : Command()
        object ReadQR : Command()
        object ReadConnectQR : Command()
        data class ShowQR(val string: String) : Command()
        object LoginSuccess : Command()
    }

    init {
        viewModelScope.launch(dispatchers.io()) {
            updateViewState()
        }
    }

    fun onCreateAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.createAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
            startInitialSync()
        }
    }

    fun onResetClicked() {
        viewModelScope.launch(dispatchers.io()) {
            syncStore.clearAll()
            updateViewState()
        }
    }

    fun onLogoutClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val currentDeviceId = syncAccountRepository.getAccountInfo().deviceId
            val result = syncAccountRepository.logout(currentDeviceId)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
        }
    }

    fun onDeviceLogoutClicked(deviceId: String) {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.logout(deviceId)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
        }
    }

    fun onEnvironmentChanged(devEnvironment: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            syncEnvDataStore.useSyncDevEnvironment = devEnvironment
            updateViewState()
        }
    }

    fun onDeleteAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.deleteAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    private fun getConnectedDevices() {
        viewModelScope.launch(dispatchers.io()) {
            when (val connectedDevices = syncAccountRepository.getConnectedDevices()) {
                is Error -> command.send(Command.ShowMessage(connectedDevices.reason))
                is Success -> {
                    viewState.emit(
                        viewState.value.copy(
                            connectedDevices = connectedDevices.data,
                        ),
                    )
                }
            }
            updateViewState()
        }
    }

    private fun startInitialSync() {
        viewModelScope.launch(dispatchers.io()) {
            when (val connectedDevices = syncAccountRepository.getConnectedDevices()) {
                is Error -> command.send(Command.ShowMessage(connectedDevices.reason))
                is Success -> {
                    viewState.emit(
                        viewState.value.copy(
                            connectedDevices = connectedDevices.data,
                        ),
                    )
                }
            }
            updateViewState()
        }
    }

    private suspend fun updateViewState() {
        val accountInfo = syncAccountRepository.getAccountInfo()
        viewState.emit(
            viewState.value.copy(
                userId = accountInfo.userId,
                deviceName = accountInfo.deviceName,
                deviceId = accountInfo.deviceId,
                isSignedIn = accountInfo.isSignedIn,
                token = syncAccountRepository.latestToken(),
                primaryKey = accountInfo.primaryKey,
                secretKey = accountInfo.secretKey,
                useDevEnvironment = syncEnvDataStore.useSyncDevEnvironment,
                environment = syncEnvDataStore.syncEnvironmentUrl,
            ),
        )
    }

    fun onReadQRClicked() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(ReadQR)
        }
    }

    fun onShowQRClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val recoveryCode = syncAccountRepository.getRecoveryCode().getOrNull() ?: return@launch
            command.send(ShowQR(recoveryCode))
        }
    }

    fun onQRScanned(contents: String) {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.processCode(contents)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    fun onConnectQRScanned(contents: String) {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.processCode(contents)
            when (result) {
                is Error -> {
                    command.send(Command.ShowMessage("$result"))
                }

                is Success -> {
                    command.send(Command.ShowMessage("${result.data}"))
                    updateViewState()
                }
            }
        }
    }

    fun onConnectStart() {
        viewModelScope.launch(dispatchers.io()) {
            val qrCode = when (val qrCodeResult = syncAccountRepository.getConnectQR()) {
                is Error -> {
                    command.send(ShowMessage("$qrCodeResult"))
                    return@launch
                }

                is Success -> qrCodeResult.data
            }
            updateViewState()
            command.send(ShowQR(qrCode))
            var polling = true
            while (polling) {
                delay(7000)
                when (val result = syncAccountRepository.pollConnectionKeys()) {
                    is Error -> {
                        command.send(Command.ShowMessage("$result"))
                    }

                    is Success -> {
                        command.send(Command.ShowMessage(result.data.toString()))
                        polling = false
                        updateViewState()
                    }
                }
            }
        }
    }

    fun onReadConnectQRClicked() {
        viewModelScope.launch(dispatchers.io()) {
            viewModelScope.launch(dispatchers.io()) {
                command.send(ReadConnectQR)
            }
        }
    }

    fun useRecoveryCode(recoveryCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            authFlow(recoveryCode)
        }
    }

    fun resetFaviconsPrompt() {
        Timber.d("Sync-Internal: Reset Favicons Prompt")
        syncFaviconFetchingStore.isFaviconsFetchingEnabled = false
        syncFaviconFetchingStore.promptShown = false
    }

    fun assertDataReceived() {
        Timber.d("Sync-Internal: Assert Data Received")
        viewModelScope.launch(dispatchers.io()) {
            val errors = assertRemoteSavedSites() + assertLocalSavedSites() + assertRemoteLogins()
            viewState.emit(
                viewState.value.copy(
                    e2eTests = E2ETest(
                        assertResult = errors.isEmpty(),
                        errors = StringBuilder().apply {
                            errors.forEach { appendLine(it) }
                        }.toString(),
                    ),
                ),
            )
        }
    }

    fun addSampleData() {
        Timber.d("Sync-Internal: Add Sample Data")
        val bookmarks = listOf(
            "https://www.example.com",
            "https://fill.dev/",
            "https://www.theverge.com/",
        )
        val favorites = listOf(
            "https://fill.dev/",
            "https://www.theverge.com/",
        )
        viewModelScope.launch(dispatchers.io()) {
            bookmarks.forEachIndexed { index, url ->
                if (savedSitesRepository.getBookmark(url) == null) {
                    savedSitesRepository.insertBookmark(url, "LocalBookmark$index")
                }
            }
            favorites.forEach { url ->
                savedSitesRepository.getBookmark(url)?.let {
                    savedSitesRepository.insertFavorite(id = it.id, url = it.url, title = it.title)
                }
            }
            if (savedSitesRepository.getFolderByName("Sync") == null) {
                savedSitesRepository.insert(BookmarkFolder(name = "Sync", parentId = SavedSitesNames.BOOKMARKS_ROOT))
            }
        }
    }

    private suspend fun assertLocalSavedSites(): List<String> {
        val errors = mutableListOf<String>()
        val localBookmarks = listOf(
            "https://www.example.com",
            "https://fill.dev/",
        )
        val localFolders = listOf("Sync")
        val localFavorites = listOf(
            "https://fill.dev/",
        )

        localBookmarks.forEach {
            if (savedSitesRepository.getBookmark(it) == null) {
                errors.add("Local Bookmark $it not found")
            }
        }
        localFolders.forEach {
            if (savedSitesRepository.getFolderByName(it) == null) {
                errors.add("Local Folder $it not found")
            }
        }
        val favoritesIds = savedSitesRepository.getFavorites().first().map { it.id }
        if (favoritesIds.size == localFavorites.size) {
            errors.add("Local Favorites count mismatch ${favoritesIds.size} != ${localFavorites.size}")
        }
        localFavorites.forEach {
            if (favoritesIds.contains(savedSitesRepository.getBookmark(it)?.id).not()) {
                errors.add("Local Favorite $it not found")
            }
        }
        return errors
    }

    private suspend fun assertRemoteSavedSites(): List<String> {
        val remoteBookmarks: Map<String, List<String>> =
            mapOf(
                "bookmarks_root" to listOf(
                    "2883eb3b-1159-4b89-ab91-73f2871ff2cb",
                    "9b6f4c8a-da90-4070-8bb3-8e67ff6ffccf",
                    "577f5646-381d-431e-a69f-00d1ea0b6a3c",
                    "b0698b4e-edd7-419f-be15-77b9d04c2312",
                    "dd760d95-7976-4b30-9ddf-9edde0a7a180",
                    "4530591f-477e-4fc1-94c6-80d23a81e88f",
                    "7cddc9be-5592-40cf-96a7-5e9576207cbc",
                ),
                "dd760d95-7976-4b30-9ddf-9edde0a7a180" to listOf(
                    "ce98b18d-1c1f-46dc-8237-fb3d921f01fa",
                    "25b1d2b5-18ef-4d90-b237-db7649514e86",
                ),
                "4530591f-477e-4fc1-94c6-80d23a81e88f" to listOf(
                    "f7d48627-636a-4637-860e-516d159c0224",
                    "68de9e3b-cb19-4765-8eba-2d189027f994",
                ),
                "7cddc9be-5592-40cf-96a7-5e9576207cbc" to listOf(
                    "b7a34394-f3e8-41a7-bcff-fab424cf6a49",
                    "5d2a2ff8-3fcf-4093-8593-9ad70612d46b",
                ),
            )

        val remoteFavorites = listOf(
            "b7a34394-f3e8-41a7-bcff-fab424cf6a49",
            "577f5646-381d-431e-a69f-00d1ea0b6a3c",
            "2883eb3b-1159-4b89-ab91-73f2871ff2cb",
        )

        val errors = mutableListOf<String>()
        remoteBookmarks.forEach { (folderId, remoteChildrenIds) ->
            savedSitesRepository.getSavedSites(folderId).first().let { savedSites ->
                savedSites.bookmarks.mapNotNull {
                    when (it) {
                        is Bookmark -> it.id
                        is BookmarkFolder -> it.id
                        else -> null
                    }
                }.let { localIds ->
                    if (localIds.containsAll(remoteChildrenIds).not()) {
                        val missing = remoteChildrenIds.filter { it !in localIds }
                        errors.add("Remote Bookmarks/Folders missing $missing")
                    }
                }
            }
        }

        savedSitesRepository.getFavorites().first().map { it.id }.let { favoriteIds ->
            remoteFavorites.forEach { id ->
                if (favoriteIds.contains(id)) {
                    errors.add("Remote Favorite $id should not exist")
                }
            }
        }

        return errors
    }

    private suspend fun assertRemoteLogins(): List<String> {
        val expected = listOf(
            "duckduckgo.com",
            "github.com",
            "stackoverflow.com",
        )
        val errors = mutableListOf<String>()
        expected.forEach {
            if (autofillStore.getCredentials(it).isEmpty()) {
                errors.add("Remote Login $it not found")
            }
        }
        return errors
    }

    private suspend fun authFlow(
        pastedCode: String,
    ) {
        val result = syncAccountRepository.processCode(pastedCode)
        when (result) {
            is Result.Success -> command.send(Command.LoginSuccess)
            is Result.Error -> {
                command.send(ShowMessage("Something went wrong"))
            }
        }
    }
}
