/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_SUGGESTION_CONFIRMED
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseViewModel.Command.AutofillLogin
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseViewModel.Command.ContinueWithoutAuthentication
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseViewModel.Command.ForceFinish
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseViewModel.Command.RequestAuthentication
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AutofillProviderChooseViewModel @Inject constructor(
    private val autofillProviderDeviceAuth: AutofillProviderDeviceAuth,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val autofillStore: InternalAutofillStore,
    private val pixel: Pixel,
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun commands(): Flow<Command> = command.receiveAsFlow().onStart {
        if (autofillProviderDeviceAuth.isAuthRequired()) {
            command.send(RequestAuthentication)
        } else {
            command.send(ContinueWithoutAuthentication)
        }
    }

    sealed class Command {
        data object RequestAuthentication : Command()
        data object ContinueWithoutAuthentication : Command()
        data class AutofillLogin(val credentials: LoginCredentials) : Command()
        data object ForceFinish : Command()
    }

    fun onUserAuthenticatedSuccessfully() {
        viewModelScope.launch(dispatchers.io()) {
            autofillProviderDeviceAuth.recordSuccessfulAuthorization()
            command.send(ContinueWithoutAuthentication)
        }
    }

    fun continueAfterAuthentication(credentialId: Long) {
        logcat(INFO) { "DDGAutofillService request to autofill login with credentialId: $credentialId" }
        viewModelScope.launch(dispatchers.io()) {
            autofillStore.getCredentialsWithId(credentialId)?.let { loginCredential ->
                loginCredential.updateLastUsedTimestamp()
                logcat(INFO) { "DDGAutofillService $credentialId found, autofilling" }
                pixel.fire(AUTOFILL_SERVICE_SUGGESTION_CONFIRMED)
                command.send(AutofillLogin(loginCredential))
            } ?: run {
                command.send(ForceFinish)
            }
        }
    }

    private fun LoginCredentials.updateLastUsedTimestamp() {
        appCoroutineScope.launch(dispatchers.io()) {
            val updated = this@updateLastUsedTimestamp.copy(lastUsedMillis = System.currentTimeMillis())
            autofillStore.updateCredentials(updated, refreshLastUpdatedTimestamp = false)
        }
    }

    private fun WebsiteLoginDetailsWithCredentials.toLoginCredentials(): LoginCredentials {
        return LoginCredentials(
            id = details.id,
            domain = details.domain,
            username = details.username,
            password = password,
            domainTitle = details.domainTitle,
            notes = notes,
            lastUpdatedMillis = details.lastUpdatedMillis,
            lastUsedMillis = details.lastUsedInMillis,
        )
    }
}
