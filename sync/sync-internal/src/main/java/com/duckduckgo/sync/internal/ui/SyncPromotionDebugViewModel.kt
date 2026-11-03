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

package com.duckduckgo.sync.internal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.promotion.InternalSyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarkAddedDialog
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksImport
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreen
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreenMultiDevice
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.ChatTabPage
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsImport
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreen
import com.duckduckgo.sync.internal.promotion.InternalSyncPromotionAppInstall
import com.duckduckgo.sync.internal.ui.SyncPromotionDebugViewModel.Command.ShowMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SyncPromotionDebugViewModel @Inject constructor(
    private val promotionDataStore: InternalSyncPromotionDataStore,
    private val promotionAppInstall: InternalSyncPromotionAppInstall,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val selectedPromotion: PromotionType = BookmarksScreen,
        val lastImpressionTime: Instant? = null,
        val dismissTime: Instant? = null,
        val installationTime: Instant? = null,
        val isInstallationTimeOverridden: Boolean = false,
    )

    sealed interface Command {
        data class ShowMessage(val message: String) : Command
    }

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    private val commands = Channel<Command>(Channel.BUFFERED)
    fun commands(): Flow<Command> = commands.receiveAsFlow()

    val promotionTypes: List<PromotionType> = PromotionType.entries.sortedBy(::labelFor)

    init {
        viewModelScope.launch { refreshPromotionData() }

        viewModelScope.launch(dispatchers.io()) {
            promotionAppInstall.installationTimeOverrideFlow.collect { override ->
                viewState.update { current ->
                    current.copy(
                        installationTime = override ?: promotionAppInstall.defaultInstallationTime(),
                        isInstallationTimeOverridden = override != null,
                    )
                }
            }
        }
    }

    fun selectPromotion(promotionType: PromotionType) {
        viewState.update { it.copy(selectedPromotion = promotionType) }
        viewModelScope.launch { refreshPromotionData() }
    }

    fun clearImpressions() {
        viewModelScope.launch {
            promotionDataStore.clearPromoImpressions(viewState.value.selectedPromotion)
            refreshPromotionData()
        }
    }

    fun changeLastImpressionTime(time: Instant) {
        viewModelScope.launch {
            promotionDataStore.setLastPromoImpressionTime(viewState.value.selectedPromotion, time)
            refreshPromotionData()
        }
    }

    fun changeDismissTime(time: Instant) {
        viewModelScope.launch {
            promotionDataStore.setPromoDismissTime(viewState.value.selectedPromotion, time)
            refreshPromotionData()
        }
    }

    fun clearDismissal() {
        viewModelScope.launch {
            promotionDataStore.clearPromoDismissal(viewState.value.selectedPromotion)
            refreshPromotionData()
        }
    }

    fun clearPromotionData() {
        viewModelScope.launch {
            val promotionType = viewState.value.selectedPromotion
            promotionDataStore.clearPromoHistory(promotionType)
            refreshPromotionData()
            commands.send(ShowMessage("'${labelFor(promotionType)}' promo data cleared"))
        }
    }

    fun clearAllPromotionData() {
        viewModelScope.launch {
            promotionDataStore.clearAllPromoHistory()
            refreshPromotionData()
            commands.send(ShowMessage("All promos cleared"))
        }
    }

    fun changeInstallationTime(time: Instant) {
        viewModelScope.launch(dispatchers.io()) { promotionAppInstall.setInstallationTimeOverride(time) }
    }

    fun resetInstallationTime() {
        viewModelScope.launch(dispatchers.io()) { promotionAppInstall.setInstallationTimeOverride(null) }
    }

    fun labelFor(promotionType: PromotionType): String = when (promotionType) {
        BookmarksScreen -> "Bookmarks screen"
        BookmarksScreenMultiDevice -> "Bookmarks screen (multi-device)"
        PasswordsScreen -> "Passwords screen"
        BookmarksImport -> "Bookmarks import"
        PasswordsImport -> "Passwords import"
        BookmarkAddedDialog -> "Bookmark added dialog"
        ChatTabPage -> "Chat tab page"
    }

    private suspend fun refreshPromotionData() {
        val promotionType = viewState.value.selectedPromotion
        val lastImpressionTime = promotionDataStore.getLastPromoImpressionTime(promotionType)
        val dismissTime = promotionDataStore.getPromoDismissTime(promotionType)
        viewState.update { current ->
            if (current.selectedPromotion == promotionType) {
                current.copy(lastImpressionTime = lastImpressionTime, dismissTime = dismissTime)
            } else {
                current
            }
        }
    }
}
