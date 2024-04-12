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

package com.duckduckgo.autofill.impl.ui.credential.passwordgeneration

import android.content.Context
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog.Companion.KEY_URL
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.impl.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesMultibinding(FragmentScope::class)
class ResultHandlerUseGeneratedPassword @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val autofillStore: InternalAutofillStore,
    private val autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor,
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector,
    private val messagePoster: AutofillMessagePoster,
    private val responseWriter: AutofillResponseWriter,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AutofillFragmentResultsPlugin {

    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        Timber.d("${this::class.java.simpleName}: processing result")

        val autofillWebMessageRequest = BundleCompat.getParcelable(result, KEY_URL, AutofillWebMessageRequest::class.java) ?: return
        if (result.getBoolean(UseGeneratedPasswordDialog.KEY_ACCEPTED)) {
            appCoroutineScope.launch(dispatchers.io()) {
                onUserAcceptedToUseGeneratedPassword(result, tabId, autofillWebMessageRequest)
            }
        } else {
            appCoroutineScope.launch(dispatchers.main()) {
                rejectGeneratedPassword(autofillWebMessageRequest)
            }
        }
    }

    fun acceptGeneratedPassword(autofillWebMessageRequest: AutofillWebMessageRequest) {
        Timber.v("Accepting generated password")
        appCoroutineScope.launch(dispatchers.io()) {
            val message = responseWriter.generateResponseForAcceptingGeneratedPassword()
            messagePoster.postMessage(message, autofillWebMessageRequest.requestId)
        }
    }

    private fun rejectGeneratedPassword(autofillWebMessageRequest: AutofillWebMessageRequest) {
        Timber.v("Rejecting generated password")
        appCoroutineScope.launch(dispatchers.io()) {
            val message = responseWriter.generateResponseForRejectingGeneratedPassword()
            messagePoster.postMessage(message, autofillWebMessageRequest.requestId)
        }
    }

    private suspend fun onUserAcceptedToUseGeneratedPassword(
        result: Bundle,
        tabId: String,
        autofillWebMessageRequest: AutofillWebMessageRequest,
    ) {
        val username = result.getString(UseGeneratedPasswordDialog.KEY_USERNAME)
        val password = result.getString(UseGeneratedPasswordDialog.KEY_PASSWORD) ?: return
        val autologinId = autoSavedLoginsMonitor.getAutoSavedLoginId(tabId)
        val matchType = existingCredentialMatchDetector.determine(autofillWebMessageRequest.requestOrigin, username, password)
        Timber.v(
            "autoSavedLoginId: %s. Match type against existing entries: %s",
            autologinId,
            matchType.javaClass.simpleName,
        )

        if (autologinId == null) {
            saveLoginIfNotAlreadySaved(matchType, autofillWebMessageRequest.requestOrigin, username, password, tabId)
        } else {
            val existingAutoSavedLogin = autofillStore.getCredentialsWithId(autologinId)
            if (existingAutoSavedLogin == null) {
                Timber.w("Can't find saved login with autosavedLoginId: $autologinId")
                saveLoginIfNotAlreadySaved(matchType, autofillWebMessageRequest.requestOrigin, username, password, tabId)
            } else {
                updateLoginIfDifferent(existingAutoSavedLogin, username, password)
            }
        }
        withContext(dispatchers.main()) {
            acceptGeneratedPassword(autofillWebMessageRequest)
        }
    }

    private suspend fun saveLoginIfNotAlreadySaved(
        matchType: ContainsCredentialsResult,
        originalUrl: String,
        username: String?,
        password: String,
        tabId: String,
    ) {
        when (matchType) {
            ContainsCredentialsResult.ExactMatch -> Timber.v("Already got an exact match; nothing to do here")
            else -> {
                autofillStore.saveCredentials(
                    originalUrl,
                    LoginCredentials(domain = originalUrl, username = username, password = password),
                )?.id?.let { savedId ->
                    Timber.i("New login saved because no exact matches were found, with ID: $savedId")
                    autoSavedLoginsMonitor.setAutoSavedLoginId(savedId, tabId)
                }
            }
        }
    }

    private suspend fun updateLoginIfDifferent(
        autosavedLogin: LoginCredentials,
        username: String?,
        password: String,
    ) {
        if (username == autosavedLogin.username && password == autosavedLogin.password) {
            Timber.i("Generated password (and username) matches existing login; nothing to do here")
        } else {
            Timber.i("Updating existing login with new username and/or password. Login id is: %s", autosavedLogin.id)
            autofillStore.updateCredentials(autosavedLogin.copy(username = username, password = password))
        }
    }

    override fun resultKey(tabId: String): String {
        return UseGeneratedPasswordDialog.resultKey(tabId)
    }
}
