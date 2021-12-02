/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.email.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Provider

class EmailProtectionViewModel(
    private val emailManager: EmailManager
) : ViewModel() {

    private val isSignedIn: StateFlow<Boolean> = emailManager.signedInFlow()

    val viewState: StateFlow<ViewState> = isSignedIn.flatMapLatest { isSignedIn ->
        emailState(isSignedIn)
    }.stateIn(viewModelScope, WhileSubscribed(), ViewState(EmailState.SignedOut))

    data class ViewState(val emailState: EmailState)

    sealed class EmailState {
        object NotSupported : EmailState()
        object SignedOut : EmailState()
        data class SignedIn(val emailAddress: String) : EmailState()
    }

    private fun emailState(isSignedIn: Boolean) = flow {
        if (emailManager.isEmailFeatureSupported()) {
            if (isSignedIn) {
                val emailAddress = emailManager.getEmailAddress() // If signed in there is always a non-null address
                emit(ViewState(EmailState.SignedIn(emailAddress!!)))
            } else {
                emit(ViewState(EmailState.SignedOut))
            }
        } else {
            emit(ViewState(EmailState.NotSupported))
        }
    }
}

@ContributesMultibinding(AppScope::class)
class EmailProtectionViewModelFactory @Inject constructor(
    private val emailManager: Provider<EmailManager>,
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(EmailProtectionViewModel::class.java) -> (EmailProtectionViewModel(emailManager.get()) as T)
                else -> null
            }
        }
    }
}
