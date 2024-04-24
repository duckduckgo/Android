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

package com.duckduckgo.autofill.sync

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.sync.CredentialsInvalidItemsViewModel.Command.NavigateToCredentials
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class CredentialsInvalidItemsViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val crendentialsSyncRepository: CredentialsSync,
) : ViewModel(), DefaultLifecycleObserver {
    data class ViewState(
        val warningVisible: Boolean = false,
        val invalidItemsSize: Int = 0,
        val hint: String = "",
    )

    sealed class Command {
        data object NavigateToCredentials : Command()
    }

    private val command = Channel<Command>(1, DROP_OLDEST)

    private val _viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = _viewState.onStart {
        viewModelScope.launch(dispatcherProvider.io()) {
            emitNewViewState()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        viewModelScope.launch(dispatcherProvider.io()) {
            emitNewViewState()
        }
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    private suspend fun emitNewViewState() {
        val invalidItems = crendentialsSyncRepository.getInvalidCredentials()
        _viewState.emit(
            ViewState(
                warningVisible = invalidItems.isNotEmpty(),
                hint = invalidItems.firstOrNull().getHint(),
                invalidItemsSize = invalidItems.size,
            ),
        )
    }

    fun onWarningActionClicked() {
        viewModelScope.launch {
            command.send(NavigateToCredentials)
        }
    }

    private fun LoginCredentials?.getHint(): String {
        val hint: String =
            this?.domainTitle.takeUnless { it.isNullOrEmpty() }
                ?: this?.domain.takeUnless { it.isNullOrEmpty() }
                ?: this?.username.takeUnless { it.isNullOrEmpty() } ?: ""
        this?.notes.takeUnless { it.isNullOrEmpty() } ?: ""

        return hint.shortenString(HINT_MAX_HINT_LENGTH)
    }

    private fun String.shortenString(maxLength: Int): String {
        return this.takeIf { it.length <= maxLength } ?: (this.take(maxLength - 3) + "...")
    }

    companion object {
        const val HINT_MAX_HINT_LENGTH = 15
    }
}
