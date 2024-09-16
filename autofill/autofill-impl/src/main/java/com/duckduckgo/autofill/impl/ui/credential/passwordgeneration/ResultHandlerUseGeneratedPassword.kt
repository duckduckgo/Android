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
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.engagement.DataAutofilledListener
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesMultibinding(AppScope::class)
class ResultHandlerUseGeneratedPassword @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val autofillStore: InternalAutofillStore,
    private val autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor,
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val autofilledListeners: PluginPoint<DataAutofilledListener>,
) : AutofillFragmentResultsPlugin {

    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        Timber.d("${this::class.java.simpleName}: processing result")

        val originalUrl = result.getString(UseGeneratedPasswordDialog.KEY_URL) ?: return
        if (result.getBoolean(UseGeneratedPasswordDialog.KEY_ACCEPTED)) {
            appCoroutineScope.launch(dispatchers.io()) {
                onUserAcceptedToUseGeneratedPassword(result, tabId, originalUrl, autofillCallback)
            }
        } else {
            appCoroutineScope.launch(dispatchers.main()) {
                autofillCallback.onRejectGeneratedPassword(originalUrl)
            }
        }
    }

    private suspend fun onUserAcceptedToUseGeneratedPassword(
        result: Bundle,
        tabId: String,
        originalUrl: String,
        callback: AutofillEventListener,
    ) {
        val username = result.getString(UseGeneratedPasswordDialog.KEY_USERNAME)
        val password = result.getString(UseGeneratedPasswordDialog.KEY_PASSWORD) ?: return
        val autologinId = autoSavedLoginsMonitor.getAutoSavedLoginId(tabId)
        val matchType = existingCredentialMatchDetector.determine(originalUrl, username, password)
        Timber.v(
            "autoSavedLoginId: %s. Match type against existing entries: %s",
            autologinId,
            matchType.javaClass.simpleName,
        )

        if (autologinId == null) {
            saveLoginIfNotAlreadySaved(matchType, originalUrl, username, password, tabId)
        } else {
            val existingAutoSavedLogin = autofillStore.getCredentialsWithId(autologinId)
            if (existingAutoSavedLogin == null) {
                Timber.w("Can't find saved login with autosavedLoginId: $autologinId")
                saveLoginIfNotAlreadySaved(matchType, originalUrl, username, password, tabId)
            } else {
                updateLoginIfDifferent(existingAutoSavedLogin, username, password)
            }
        }
        withContext(dispatchers.main()) {
            callback.onAcceptGeneratedPassword(originalUrl)
        }

        autofilledListeners.getPlugins().forEach {
            it.onUsedGeneratedPassword()
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
