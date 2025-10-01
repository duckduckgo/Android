/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.setup

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.ui.setup.SyncSetupDeepLinkViewModel.ViewMode.CreatingAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import javax.inject.*

@ContributesViewModel(ActivityScope::class)
class SyncSetupDeepLinkViewModel @Inject constructor() : ViewModel() {

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart {
        viewState.emit(ViewState(CreatingAccount))
    }

    data class ViewState(
        val viewMode: ViewMode = CreatingAccount,
    )

    sealed class ViewMode {
        data object CreatingAccount : ViewMode()
    }
}
