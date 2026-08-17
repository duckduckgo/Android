/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.v2

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.CancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_EXCHANGE
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ReadSyncCodeViewModel @AssistedInject constructor(
    @Assisted val entryPoint: SyncEntryPoint,
    private val clipboard: Clipboard,
    private val syncPixels: SyncPixels,
    private val codeDispatcher: SyncCodeDispatcher,
) : ViewModel() {
    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    private val screenType: ScreenType
        get() = when (entryPoint) {
            SyncEntryPoint.SYNC_NEW_ACCOUNT -> SYNC_CONNECT
            SyncEntryPoint.ADD_DEVICE -> SYNC_EXCHANGE
            SyncEntryPoint.RECOVER_SYNCED_DATA -> SYNC_EXCHANGE
        }

    fun onScannerScreenShown() {
        syncPixels.fireScanCodeScreenShown(screenType)
    }

    fun onManualEntryScreenShown() {
        syncPixels.fireSyncSetupManualCodeScreenShown(screenType)
    }

    fun onUserCanceled() {
        val reason = if (codeDispatcher.isV2ExchangeUnderway()) {
            CancellationReason.CANCELLED_BEFORE_FINISHED
        } else {
            CancellationReason.SCANNING_CANCELLED
        }
        syncPixels.fireSyncSetupAbandoned(screenType, reason)
    }

    fun pasteSyncCode() {
        val code = clipboard.pasteFromClipboard()
        viewModelScope.launch {
            if (code.isBlank()) {
                syncPixels.fireSyncSetupCodePastedParseFailure(screenType, SetupFailureReason.UNRECOGNIZED_CODE)
                _commands.send(Command.ShowMessage(R.string.sync_simplified_scanner_manual_entry_invalid_code_message))
            } else {
                _commands.send(Command.StartSyncProcess(SyncCodeSource.Pasted(code, entryPoint)))
            }
        }
    }

    fun processScannedCode(code: String) {
        viewModelScope.launch {
            _commands.send(Command.StartSyncProcess(SyncCodeSource.Scanned(code, entryPoint)))
        }
    }

    sealed interface Command {
        data class ShowMessage(
            @StringRes val message: Int,
        ) : Command

        data class StartSyncProcess(
            val source: SyncCodeSource,
        ) : Command
    }

    @AssistedFactory
    interface Factory {
        fun create(entryPoint: SyncEntryPoint): ReadSyncCodeViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val entryPoint: SyncEntryPoint,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(entryPoint) as T
            }
        }
    }
}
