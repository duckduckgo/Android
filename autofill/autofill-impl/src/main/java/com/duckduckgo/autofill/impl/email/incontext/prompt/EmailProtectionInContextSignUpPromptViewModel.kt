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

package com.duckduckgo.autofill.impl.email.incontext.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult.Cancel
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult.DoNotShowAgain
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult.SignUp
import com.duckduckgo.autofill.impl.email.incontext.prompt.EmailProtectionInContextSignUpPromptViewModel.Command.FinishWithResult
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_PROMPT_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_PROTECTION_IN_CONTEXT_PROMPT_NEVER_AGAIN
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class EmailProtectionInContextSignUpPromptViewModel @Inject constructor(
    val dispatchers: DispatcherProvider,
    val pixel: Pixel,
) : ViewModel() {

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    sealed interface Command {
        data class FinishWithResult(val result: EmailProtectionInContextSignUpResult) : Command
    }

    fun onProtectEmailButtonPressed() {
        pixel.fire(EMAIL_PROTECTION_IN_CONTEXT_PROMPT_CONFIRMED)
        FinishWithResult(SignUp).send()
    }

    fun onCloseButtonPressed() {
        pixel.fire(EMAIL_PROTECTION_IN_CONTEXT_PROMPT_DISMISSED)
        FinishWithResult(Cancel).send()
    }

    fun onDoNotShowAgainButtonPressed() {
        pixel.fire(EMAIL_PROTECTION_IN_CONTEXT_PROMPT_NEVER_AGAIN)
        FinishWithResult(DoNotShowAgain).send()
    }

    private fun Command.send() {
        viewModelScope.launch(dispatchers.io()) {
            _commands.send(this@send)
        }
    }
}
