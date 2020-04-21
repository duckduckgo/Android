/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.fire.fireproofwebsite.ui

import androidx.lifecycle.*
import com.duckduckgo.app.fire.FireproofWebsiteDao
import com.duckduckgo.app.fire.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesViewModel.Command.ConfirmDeletePreservedWebsite
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FireproofWebsitesViewModel(
    private val dao: FireproofWebsiteDao,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    data class ViewState(
        val fireproofWebsitesEntities: List<FireproofWebsiteEntity> = emptyList()
    )

    sealed class Command {
        class ConfirmDeletePreservedWebsite(val entity: FireproofWebsiteEntity) : Command()
    }

    companion object {
        private const val MIN_BOOKMARKS_FOR_SEARCH = 3
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val bookmarks: LiveData<List<FireproofWebsiteEntity>> = dao.fireproofWebsitesEntities()
    private val bookmarksObserver = Observer<List<FireproofWebsiteEntity>> { onPreservedCookiesEntitiesChanged(it!!) }

    init {
        viewState.value = ViewState()
        bookmarks.observeForever(bookmarksObserver)
    }

    override fun onCleared() {
        super.onCleared()
        bookmarks.removeObserver(bookmarksObserver)
    }

    private fun onPreservedCookiesEntitiesChanged(entities: List<FireproofWebsiteEntity>) {
        viewState.value = viewState.value?.copy(
            fireproofWebsitesEntities = entities
        )
    }

    fun onDeleteRequested(entity: FireproofWebsiteEntity) {
        command.value = ConfirmDeletePreservedWebsite(entity)
    }

    fun delete(entity: FireproofWebsiteEntity) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io()) {
                dao.delete(entity)
            }
        }
    }
}