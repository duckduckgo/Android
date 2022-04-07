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

package com.duckduckgo.app.downloads

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.downloads.DownloadViewItem.Empty
import com.duckduckgo.app.downloads.DownloadViewItem.Header
import com.duckduckgo.app.downloads.DownloadViewItem.Item
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.CancelDownload
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.DisplayMessage
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.DisplayUndoMessage
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.OpenFile
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.ShareFile
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.TimeDiffFormatter
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.store.DownloadsRepository
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDateTime
import java.io.File
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DownloadsViewModel @Inject constructor(
    private val timeDiffFormatter: TimeDiffFormatter,
    private val downloadsRepository: DownloadsRepository,
    private val dispatcher: DispatcherProvider,
) : ViewModel(), DownloadsItemListener {

    data class ViewState(
        val enableSearch: Boolean = false,
        val downloadItems: List<DownloadViewItem> = emptyList(),
        val filteredItems: List<DownloadViewItem> = emptyList()
    )

    sealed class Command {
        data class DisplayMessage(@StringRes val messageId: Int, val arg: String = "") : Command()
        data class DisplayUndoMessage(@StringRes val messageId: Int, val arg: String = "", val items: List<DownloadItem> = emptyList()) : Command()
        data class OpenFile(val item: DownloadItem) : Command()
        data class ShareFile(val item: DownloadItem) : Command()
        data class CancelDownload(val item: DownloadItem) : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, DROP_OLDEST)

    fun downloads() {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.getDownloadsAsFlow().collect {
                val itemsList = it.mapToDownloadViewItems()
                viewState.emit(
                    currentViewState().copy(downloadItems = itemsList, filteredItems = itemsList)
                )
            }
        }
    }

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun deleteAllDownloadedItems() {
        viewModelScope.launch(dispatcher.io()) {
            val itemsToDelete = downloadsRepository.getDownloads()
            if (itemsToDelete.isNotEmpty()) {
                downloadsRepository.deleteAll()
                command.send(DisplayUndoMessage(messageId = R.string.downloadsAllFilesDeletedMessage, items = itemsToDelete))
            }
        }
    }

    fun delete(item: DownloadItem) {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.delete(item.id)
            command.send(DisplayMessage(R.string.downloadsFileNotFoundErrorMessage))
        }
    }

    fun insert(items: List<DownloadItem>) {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.insertAll(items)
        }
    }

    fun deleteFilesFromDisk(items: List<DownloadItem>) {
        items.forEach {
            File(it.filePath).delete()
        }
    }

    fun onQueryTextChange(newText: String) {
        val filtered = LinkedHashMap<Header, List<Item>>()
        viewModelScope.launch(dispatcher.io()) {

            currentViewState().downloadItems.forEach { item ->
                if (item is Header) {
                    filtered[item] = mutableListOf()
                } else if (item is Item) {
                    if (item.downloadItem.fileName.lowercase().contains(newText.lowercase())) {
                        val list = filtered[filtered.keys.last()]
                        val newList = list?.plus(item) ?: listOf(item)
                        filtered[filtered.keys.last()] = newList
                    }
                }
            }

            val list = mutableListOf<DownloadViewItem>()
            filtered.forEach {
                if (it.value.isNotEmpty()) {
                    list.add(it.key)
                    list.addAll(it.value)
                }
            }

            if (list.isEmpty()) {
                list.add(Empty)
            }

            viewState.emit(
                currentViewState().copy(
                    filteredItems = list
                )
            )
        }
    }

    override fun onItemClicked(item: DownloadItem) {
        viewModelScope.launch { command.send(OpenFile(item)) }
    }

    override fun onShareItemClicked(item: DownloadItem) {
        viewModelScope.launch { command.send(ShareFile(item)) }
    }

    override fun onDeleteItemClicked(item: DownloadItem) {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.delete(item.id)
            command.send(DisplayUndoMessage(messageId = R.string.downloadsFileDeletedMessage, arg = item.fileName, items = listOf(item)))
        }
    }

    override fun onCancelItemClicked(item: DownloadItem) {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.delete(item.id)
            command.send(CancelDownload(item))
        }
    }

    private fun DownloadItem.mapToDownloadViewItem(): DownloadViewItem = Item(this)

    private fun List<DownloadItem>.mapToDownloadViewItems(): List<DownloadViewItem> {
        if (this.isEmpty()) return listOf(Empty)

        val itemViews = mutableListOf<DownloadViewItem>()
        var previousDate = timeDiffFormatter.formatTimePassedInDaysWeeksMonthsYears(
            startLocalDateTime = LocalDateTime.parse(this[0].createdAt)
        )

        this.forEachIndexed { index, downloadItem ->
            if (index == 0) {
                itemViews.add(Header(previousDate))
                itemViews.add(downloadItem.mapToDownloadViewItem())
            } else {
                val thisDate = timeDiffFormatter.formatTimePassedInDaysWeeksMonthsYears(
                    startLocalDateTime = LocalDateTime.parse(downloadItem.createdAt)
                )
                if (previousDate == thisDate) {
                    itemViews.add(downloadItem.mapToDownloadViewItem())
                } else {
                    itemViews.add(Header(thisDate))
                    itemViews.add(downloadItem.mapToDownloadViewItem())
                    previousDate = thisDate
                }
            }
        }

        return itemViews
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}
