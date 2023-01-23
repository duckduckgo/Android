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
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncInitialSetupViewModel
@Inject
constructor(
    private val syncDeviceIds: SyncDeviceIds,
) : ViewModel() {

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart {
        updateViewState()
    }

    data class ViewState(
        val userId: String = "",
        val deviceName: String = "",
        val deviceId: String = "",
    )

    private suspend fun updateViewState() {
        viewState.emit(
            viewState.value.copy(
                userId = syncDeviceIds.userId(),
                deviceName = syncDeviceIds.deviceName(),
                deviceId = syncDeviceIds.deviceId(),
            ),
        )
    }
}
