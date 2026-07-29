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
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@ContributesViewModel(ActivityScope::class)
class ReadSyncCodeViewModel @Inject constructor(
    private val clipboard: Clipboard,
) : ViewModel() {
    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    private var scanCodeJob: Job? = null

    fun pasteSyncCode() {
        val code = clipboard.pasteFromClipboard()
        viewModelScope.launch {
            processCode(code, invalidCodeMessage = R.string.sync_scanner_v2_manual_entry_invalid_code_pasted)
        }
    }

    fun processScannedCode(code: String) {
        // The camera decodes continuously, emitting the same barcode for every frame it stays in
        // view, so without debouncing a single scan would repeatedly start the sync flow.
        if (scanCodeJob?.isActive == true) return
        scanCodeJob = viewModelScope.launch {
            processCode(code, invalidCodeMessage = R.string.sync_scanner_v2_scan_qr_code_invalid_code_scanned)
            delay(SCAN_CODE_DEBOUNCE_DURATION)
        }
    }

    private suspend fun processCode(
        code: String,
        @StringRes invalidCodeMessage: Int,
    ) {
        if (code.isBlank()) {
            _commands.send(Command.ShowMessage(invalidCodeMessage))
        } else {
            _commands.send(Command.StartSyncProcess(code))
        }
    }

    sealed interface Command {
        data class ShowMessage(
            @StringRes val message: Int,
        ) : Command

        data class StartSyncProcess(
            val syncCode: String,
        ) : Command
    }

    companion object {
        private val SCAN_CODE_DEBOUNCE_DURATION = 5.seconds
    }
}
