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
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.engagement.DataAutofilledListener
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller.BackFillResult.BackFillNotSupported
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller.BackFillResult.BackFillSupported
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat

@ContributesMultibinding(AppScope::class)
class ResultHandlerUseGeneratedPassword @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val autofillStore: InternalAutofillStore,
    private val autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor,
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val autofilledListeners: PluginPoint<DataAutofilledListener>,
    private val usernameBackFiller: UsernameBackFiller,
) : AutofillFragmentResultsPlugin {

    override suspend fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
        webView: WebView?,
    ) {
        logcat { "${this::class.java.simpleName}: processing result" }

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
        val backFillUsernameIfRequired = result
            .getString(UseGeneratedPasswordDialog.KEY_USERNAME)
            .backFillUsernameIfSupported(originalUrl)

        val username = backFillUsernameIfRequired.first

        val password = result.getString(UseGeneratedPasswordDialog.KEY_PASSWORD) ?: return
        val autologinId = autoSavedLoginsMonitor.getAutoSavedLoginId(tabId)
        val matchType = existingCredentialMatchDetector.determine(originalUrl, username, password)
        logcat(VERBOSE) { "autoSavedLoginId: $autologinId. Match type against existing entries: ${matchType.javaClass.simpleName}" }

        if (autologinId == null) {
            saveLoginIfNotAlreadySaved(matchType, originalUrl, username, password, tabId)
        } else {
            val existingAutoSavedLogin = autofillStore.getCredentialsWithId(autologinId)
            if (existingAutoSavedLogin == null) {
                logcat(WARN) { "Can't find saved login with autosavedLoginId: $autologinId" }
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
            ContainsCredentialsResult.ExactMatch -> logcat(VERBOSE) { "Already got an exact match; nothing to do here" }
            ContainsCredentialsResult.UsernameMatchMissingPassword -> {
                logcat(VERBOSE) { "Will update existing password(s) which are username matches but missing a password" }
                updateCredentialsIfPasswordMissing(originalUrl, username, password)
            }
            ContainsCredentialsResult.UsernameMatchDifferentPassword -> {
                logcat(VERBOSE) { "There's a matching username already with a different password saved. No automatic action taken." }
            }
            else -> {
                autofillStore.saveCredentials(
                    originalUrl,
                    LoginCredentials(domain = originalUrl, username = username, password = password),
                )?.id?.let { savedId ->
                    logcat(INFO) { "New login saved because no exact matches were found, with ID: $savedId" }
                    autoSavedLoginsMonitor.setAutoSavedLoginId(savedId, tabId)
                }
            }
        }
    }

    private suspend fun updateCredentialsIfPasswordMissing(
        originalUrl: String,
        username: String?,
        password: String,
    ) {
        withContext(dispatchers.io()) {
            autofillStore.getCredentials(originalUrl)
                .filter { it.username == username }
                .filter { it.password.isNullOrEmpty() }
                .also { list ->
                    logcat(VERBOSE) {
                        "Found ${list.size} credentials with missing password for username=$username and url=$originalUrl"
                    }
                }
                .map { it.copy(password = password) }
                .forEach { autofillStore.updateCredentials(originalUrl, it, CredentialUpdateType.Password) }
        }
    }

    private suspend fun updateLoginIfDifferent(
        autosavedLogin: LoginCredentials,
        username: String?,
        password: String,
    ) {
        if (username == autosavedLogin.username && password == autosavedLogin.password) {
            logcat(INFO) { "Generated password (and username) matches existing login; nothing to do here" }
        } else {
            logcat(INFO) { "Updating existing login with new username and/or password. Login id is: ${autosavedLogin.id}" }
            autofillStore.updateCredentials(autosavedLogin.copy(username = username, password = password))
        }
    }

    private suspend fun String?.backFillUsernameIfSupported(currentUrl: String): Pair<String?, Boolean> {
        // determine if we can and should use a partial previous submission's username
        val result = usernameBackFiller.isBackFillingUsernameSupported(this, currentUrl)
        return when (result) {
            is BackFillSupported -> Pair(result.username, true)
            is BackFillNotSupported -> Pair(this, false)
        }
    }

    override fun resultKey(tabId: String): String {
        return UseGeneratedPasswordDialog.resultKey(tabId)
    }
}
