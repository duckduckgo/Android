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

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.R.string
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.ShowQRCodeViewModel.Command.LoginSucess
import com.duckduckgo.sync.impl.ui.ShowQRCodeViewModel.Command.ShowMessage
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class ShowQRCodeViewModel @Inject constructor(
    private val qrEncoder: QREncoder,
    private val syncAccountRepository: SyncAccountRepository,
    private val clipboard: Clipboard,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): Flow<ViewState> = viewState.onStart {
        pollConnectionKeys()
    }

    private fun pollConnectionKeys() {
        viewModelScope.launch(dispatchers.io()) {
            showQRCode()
            var polling = true
            while (polling) {
                delay(POLLING_INTERVAL)
                when (syncAccountRepository.pollConnectionKeys()) {
                    is Success -> {
                        command.send(LoginSucess)
                        polling = false
                    }

                    else -> {
                        // noop - keep polling
                    }
                }
            }
        }
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val qrCodeBitmap: Bitmap? = null,
    )

    sealed class Command {
        data class ShowMessage(val messageId: Int) : Command()
        object LoginSucess : Command()
        object Error : Command()
    }

    private suspend fun showQRCode() {
        when (val result = syncAccountRepository.getConnectQR()) {
            is Error -> {
                command.send(Command.Error)
            }

            is Success -> {
                val qrBitmap = withContext(dispatchers.io()) {
                    qrEncoder.encodeAsBitmap(result.data, R.dimen.qrSizeXLarge, R.dimen.qrSizeXLarge)
                }
                viewState.emit(
                    viewState.value.copy(
                        qrCodeBitmap = qrBitmap,
                    ),
                )
            }
        }
    }

    fun onCopyCodeClicked() {
        viewModelScope.launch(dispatchers.io()) {
            when (val result = syncAccountRepository.getConnectQR()) {
                is Error -> command.send(Command.Error)
                is Success -> {
                    clipboard.copyToClipboard(result.data)
                    command.send(ShowMessage(string.sync_code_copied_message))
                }
            }
        }
    }

    companion object {
        const val POLLING_INTERVAL = 5000L
    }
}
