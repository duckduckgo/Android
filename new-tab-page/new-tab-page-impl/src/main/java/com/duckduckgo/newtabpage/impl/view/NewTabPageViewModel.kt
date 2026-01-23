/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.view

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.newtabpage.api.NewTabPageSection.FAVOURITES
import com.duckduckgo.newtabpage.api.NewTabPageSection.SHORTCUTS
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.newtabpage.api.NewTabPageSectionProvider
import com.duckduckgo.newtabpage.impl.pixels.NewTabPixels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class NewTabPageViewModel @Inject constructor(
    private val newTabSectionsProvider: NewTabPageSectionProvider,
    private val newTabPixels: NewTabPixels,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val sections: List<NewTabPageSectionPlugin> = emptyList(),
        val loading: Boolean = true,
        val showDax: Boolean = false,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    override fun onResume(owner: LifecycleOwner) {
        refreshViews()
    }

    private fun refreshViews() {
        newTabSectionsProvider.provideSections().onEach { sections ->
            val showDax = sections.none { it.name == SHORTCUTS.name || it.name == FAVOURITES.name }
            _viewState.update { ViewState(sections = sections, loading = false, showDax = showDax) }
        }.distinctUntilChanged().onStart {
            _viewState.update { ViewState() }
        }.flowOn(dispatcherProvider.io()).launchIn(viewModelScope)
    }

    fun onCustomisePageClicked() {
        newTabPixels.fireCustomizePagePressedPixel()
    }
}
