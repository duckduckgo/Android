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

package com.duckduckgo.app.privacy.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.privacy.model.UserAllowListedDomain
import com.duckduckgo.app.privacy.ui.AllowListViewModel.Command.*
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AllowListViewModel @Inject constructor(
    private val dao: UserAllowListDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val showAllowList: Boolean = true,
        val allowList: List<UserAllowListedDomain> = emptyList(),
    )

    sealed class Command {
        data object ShowAdd : Command()
        class ShowEdit(val entry: UserAllowListedDomain) : Command()
        class ConfirmDelete(val entry: UserAllowListedDomain) : Command()
        data object ShowAllowListFormatError : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val entries: LiveData<List<UserAllowListedDomain>> = dao.all()
    private val observer = Observer<List<UserAllowListedDomain>> { onUserAllowListChanged(it!!) }

    init {
        viewState.value = ViewState()
        entries.observeForever(observer)
    }

    override fun onCleared() {
        super.onCleared()
        entries.removeObserver(observer)
    }

    private fun onUserAllowListChanged(entries: List<UserAllowListedDomain>) {
        viewState.value = viewState.value?.copy(
            showAllowList = entries.isNotEmpty(),
            allowList = entries,
        )
    }

    fun onAddRequested() {
        command.value = ShowAdd
    }

    fun onEntryAdded(entry: UserAllowListedDomain) {
        if (!UriString.isValidDomain(entry.domain)) {
            command.value = ShowAllowListFormatError
            return
        }
        appCoroutineScope.launch(dispatchers.io()) {
            addEntryToDatabase(entry)
        }
    }

    fun onEditRequested(entry: UserAllowListedDomain) {
        command.value = ShowEdit(entry)
    }

    fun onEntryEdited(
        old: UserAllowListedDomain,
        new: UserAllowListedDomain,
    ) {
        if (!UriString.isValidDomain(new.domain)) {
            command.value = ShowAllowListFormatError
            return
        }
        appCoroutineScope.launch(dispatchers.io()) {
            deleteEntryFromDatabase(old)
            addEntryToDatabase(new)
        }
    }

    fun onDeleteRequested(entry: UserAllowListedDomain) {
        command.value = ConfirmDelete(entry)
    }

    fun onEntryDeleted(entry: UserAllowListedDomain) {
        appCoroutineScope.launch(dispatchers.io()) {
            deleteEntryFromDatabase(entry)
        }
    }

    private suspend fun addEntryToDatabase(entry: UserAllowListedDomain) {
        withContext(dispatchers.io()) { dao.insert(entry) }
    }

    private suspend fun deleteEntryFromDatabase(entry: UserAllowListedDomain) {
        withContext(dispatchers.io()) { dao.delete(entry) }
    }
}
