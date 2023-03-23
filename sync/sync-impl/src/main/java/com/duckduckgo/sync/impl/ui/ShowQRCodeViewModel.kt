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

package com.duckduckgo.sync.impl.ui

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncRepository
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.*

@ContributesViewModel(ActivityScope::class)
class ShowQRCodeViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val command = Channel<SyncLoginViewModel.Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): Flow<ViewState> = viewState.onStart { updateViewState() }
    fun commands(): Flow<SyncLoginViewModel.Command> = command.receiveAsFlow()

    data class ViewState(
        val qrCode: String? = null,
    )

    sealed class Command {
        object ReadQRCode : Command()
        object ShowQRCode : Command()
        object ReadTextCode : Command()
        object LoginSucess : Command()
        object Error : Command()
    }

    private suspend fun updateViewState() {
        val result = syncRepository.getConnectQR()
        when (result) {
            is Error -> TODO()
            is Success -> {
                viewState.emit(
                    viewState.value.copy(
                        qrCode = result.data
                    ),
                )
            }
        }
    }

}
