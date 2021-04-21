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

import androidx.annotation.UiThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.asLiveData
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import timber.log.Timber

interface EmailManager : LifecycleObserver {
    fun signedInFlow(): Flow<Boolean>
    fun getAlias(): String?
    fun isSignedIn(): Boolean
    fun storeCredentials(token: String, username: String)
    fun signOut()
    fun getEmailAddress(): String?
    var nextAlias: String?
}

@FlowPreview
@ExperimentalCoroutinesApi
class AppEmailManager(
    private val emailService: EmailService,
    private val emailDataStore: EmailDataStore,
    private val dispatcherProvider: DispatcherProvider
) : EmailManager, LifecycleObserver {

    override var nextAlias: String? = null

    private val nextAliasLiveData: LiveData<String?> = emailDataStore.nextAliasFlow().asLiveData()

    private val alias = Observer<String?> { alias ->
        nextAlias = alias
    }

    private val isSignedInChannel = ConflatedBroadcastChannel(isSignedIn())

    override fun signedInFlow(): Flow<Boolean> = isSignedInChannel.asFlow()

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        nextAliasLiveData.observeForever(alias)
    }

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        nextAliasLiveData.removeObserver(alias)
    }

    override fun getAlias(): String? = consumeAlias()

    override fun isSignedIn(): Boolean {
        return !emailDataStore.emailToken.isNullOrBlank() && !emailDataStore.emailUsername.isNullOrBlank() && !emailDataStore.nextAlias.isNullOrBlank()
    }

    override fun storeCredentials(token: String, username: String) {
        emailDataStore.emailToken = token
        emailDataStore.emailUsername = username
        GlobalScope.launch(dispatcherProvider.io()) {
            generateNewAlias()
            isSignedInChannel.send(true)
        }
    }

    override fun signOut() {
        GlobalScope.launch(dispatcherProvider.io()) {
            emailDataStore.clearEmailData()
            isSignedInChannel.send(false)
        }
    }

    override fun getEmailAddress(): String? {
        return emailDataStore.emailUsername?.let {
            "$it$DUCK_EMAIL_DOMAIN"
        }
    }

    private fun consumeAlias(): String? {
        val alias = nextAlias
        emailDataStore.clearNextAlias()
        GlobalScope.launch(dispatcherProvider.io()) {
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
