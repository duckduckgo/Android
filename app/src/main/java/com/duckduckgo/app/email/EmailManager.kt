/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email

import androidx.lifecycle.LifecycleObserver
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

interface EmailManager : LifecycleObserver {
    fun signedInFlow(): StateFlow<Boolean>
    fun getAlias(): String?
    fun isSignedIn(): Boolean
    fun storeCredentials(token: String, username: String)
    fun signOut()
    fun getEmailAddress(): String?
}

@FlowPreview
@ExperimentalCoroutinesApi
class AppEmailManager(
    private val emailService: EmailService,
    private val emailDataStore: EmailDataStore,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope
) : EmailManager {

    private val nextAliasFlow = emailDataStore.nextAliasFlow()

    private val isSignedInStateFlow = MutableStateFlow(isSignedIn())
    override fun signedInFlow(): StateFlow<Boolean> = isSignedInStateFlow.asStateFlow()

    override fun getAlias(): String? = consumeAlias()

    override fun isSignedIn(): Boolean {
        return !emailDataStore.emailToken.isNullOrBlank() && !emailDataStore.emailUsername.isNullOrBlank()
    }

    override fun storeCredentials(token: String, username: String) {
        emailDataStore.emailToken = token
        emailDataStore.emailUsername = username
        appCoroutineScope.launch(dispatcherProvider.io()) {
            generateNewAlias()
            isSignedInStateFlow.emit(true)
        }
    }

    override fun signOut() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            emailDataStore.clearEmailData()
            isSignedInStateFlow.emit(false)
        }
    }

    override fun getEmailAddress(): String? {
        return emailDataStore.emailUsername?.let {
            "$it$DUCK_EMAIL_DOMAIN"
        }
    }

    private fun consumeAlias(): String? {
        val alias = nextAliasFlow.value
        emailDataStore.clearNextAlias()
        appCoroutineScope.launch(dispatcherProvider.io()) {
            generateNewAlias()
        }
        return alias
    }

    private suspend fun generateNewAlias() {
        fetchAliasFromService()
    }

    private suspend fun fetchAliasFromService() {
        emailDataStore.emailToken?.let { token ->
            runCatching {
                emailService.newAlias("Bearer $token")
            }.onSuccess { alias ->
                emailDataStore.nextAlias = if (alias.address.isBlank()) {
                    null
                } else {
                    "${alias.address}$DUCK_EMAIL_DOMAIN"
                }
            }.onFailure {
                Timber.w(it, "Failed to fetch alias")
            }
        }
    }

    companion object {
        const val DUCK_EMAIL_DOMAIN = "@duck.com"
    }

    private fun EmailDataStore.clearEmailData() {
        emailToken = null
        emailUsername = null
        nextAlias = null
    }

    private fun EmailDataStore.clearNextAlias() {
        nextAlias = null
    }
}
