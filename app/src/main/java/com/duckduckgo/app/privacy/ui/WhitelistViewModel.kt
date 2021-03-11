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
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.UserWhitelistedDomain
import com.duckduckgo.app.privacy.ui.WhitelistViewModel.Command.*
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Singleton

class WhitelistViewModel(
    private val dao: UserWhitelistDao,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    data class ViewState(
        val showWhitelist: Boolean = true,
        val whitelist: List<UserWhitelistedDomain> = emptyList()
    )

    sealed class Command {
        object ShowAdd : Command()
        class ShowEdit(val entry: UserWhitelistedDomain) : Command()
        class ConfirmDelete(val entry: UserWhitelistedDomain) : Command()
        object ShowWhitelistFormatError : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val entries: LiveData<List<UserWhitelistedDomain>> = dao.all()
    private val observer = Observer<List<UserWhitelistedDomain>> { onUserWhitelistChanged(it!!) }

    init {
        viewState.value = ViewState()
        entries.observeForever(observer)
    }

    override fun onCleared() {
        super.onCleared()
        entries.removeObserver(observer)
    }

    private fun onUserWhitelistChanged(entries: List<UserWhitelistedDomain>) {
        viewState.value = viewState.value?.copy(
            showWhitelist = entries.isNotEmpty(),
            whitelist = entries
        )
    }

    fun onAddRequested() {
        command.value = ShowAdd
    }

    fun onEntryAdded(entry: UserWhitelistedDomain) {
        if (!UriString.isValidDomain(entry.domain)) {
            command.value = ShowWhitelistFormatError
            return
        }
        GlobalScope.launch(dispatchers.io()) {
            addEntryToDatabase(entry)
        }
    }

    fun onEditRequested(entry: UserWhitelistedDomain) {
        command.value = ShowEdit(entry)
    }

    fun onEntryEdited(old: UserWhitelistedDomain, new: UserWhitelistedDomain) {
        if (!UriString.isValidDomain(new.domain)) {
            command.value = ShowWhitelistFormatError
            return
        }
        GlobalScope.launch(dispatchers.io()) {
            deleteEntryFromDatabase(old)
            addEntryToDatabase(new)
        }
    }

    fun onDeleteRequested(entry: UserWhitelistedDomain) {
        command.value = ConfirmDelete(entry)
    }

    fun onEntryDeleted(entry: UserWhitelistedDomain) {
        GlobalScope.launch(dispatchers.io()) {
            deleteEntryFromDatabase(entry)
        }
    }

    private suspend fun addEntryToDatabase(entry: UserWhitelistedDomain) {
        withContext(dispatchers.io()) { dao.insert(entry) }
    }

    private suspend fun deleteEntryFromDatabase(entry: UserWhitelistedDomain) {
        withContext(dispatchers.io()) { dao.delete(entry) }
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
class WhitelistViewModelFactoryModule {
    @Provides
    @Singleton
    @IntoSet
    fun provideWhitelistViewModelFactory(
        dao: UserWhitelistDao
    ): ViewModelFactoryPlugin {
        return WhitelistViewModelFactory(dao)
    }
}

private class WhitelistViewModelFactory(
    private val dao: UserWhitelistDao
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(WhitelistViewModel::class.java) -> (WhitelistViewModel(dao) as T)
                else -> null
            }
        }
    }
}
