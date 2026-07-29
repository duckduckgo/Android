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
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class ReadSyncCodeViewModel @Inject constructor(
    private val clipboard: Clipboard,
) : ViewModel() {
    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    fun pasteSyncCode() {
        val url = clipboard.pasteFromClipboard()
        val code = SyncBarcodeUrl.parseUrl(url)
        viewModelScope.launch {
            val command = if (code == null) {
                Command.ShowMessage(R.string.sync_scanner_v2_manual_entry_invalid_code_pasted)
            } else {
                Command.StartSyncProcess(url)
            }
            _commands.send(command)
        }
    }

    sealed interface Command {
        data class ShowMessage(
            @StringRes val message: Int,
        ) : Command

        data class StartSyncProcess(
            val syncUrl: String,
        ) : Command
    }
}
