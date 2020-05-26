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
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesViewModel.Command.ConfirmDeleteFireproofWebsite
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.FIREPROOF_WEBSITE_DELETED
import kotlinx.coroutines.launch

class FireproofWebsitesViewModel(
    private val dao: FireproofWebsiteDao,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel
) : ViewModel() {

    data class ViewState(
        val fireproofWebsitesEntities: List<FireproofWebsiteEntity> = emptyList()
    )

    sealed class Command {
        class ConfirmDeleteFireproofWebsite(val entity: FireproofWebsiteEntity) : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val fireproofWebsites: LiveData<List<FireproofWebsiteEntity>> = dao.fireproofWebsitesEntities()
    private val fireproofWebsitesObserver = Observer<List<FireproofWebsiteEntity>> { onPreservedCookiesEntitiesChanged(it!!) }

    init {
        viewState.value = ViewState()
        fireproofWebsites.observeForever(fireproofWebsitesObserver)
    }

    override fun onCleared() {
        super.onCleared()
        fireproofWebsites.removeObserver(fireproofWebsitesObserver)
    }

    private fun onPreservedCookiesEntitiesChanged(entities: List<FireproofWebsiteEntity>) {
        viewState.value = viewState.value?.copy(
            fireproofWebsitesEntities = entities
        )
    }

    fun onDeleteRequested(entity: FireproofWebsiteEntity) {
        command.value = ConfirmDeleteFireproofWebsite(entity)
    }

    fun delete(entity: FireproofWebsiteEntity) {
        viewModelScope.launch(dispatcherProvider.io()) {
            dao.delete(entity)
            pixel.fire(FIREPROOF_WEBSITE_DELETED)
        }
    }
}
