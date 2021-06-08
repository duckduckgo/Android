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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface EmailManager : LifecycleObserver {
    fun signedInFlow(): StateFlow<Boolean>
    fun getAlias(): String?
    fun isSignedIn(): Boolean
    fun storeCredentials(token: String, username: String)
    fun signOut()
    fun getEmailAddress(): String?
    fun waitlistState(): AppEmailManager.WaitlistState
    fun joinWaitlist(timestamp: Int, token: String)
    fun getInviteCode(): String
    fun doesCodeAlreadyExist(): Boolean
    suspend fun fetchInviteCode(): AppEmailManager.FetchCodeResult
}

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
            isSignedInStateFlow.emit(isSignedIn())
            generateNewAlias()
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

    override fun waitlistState(): WaitlistState {
        if (emailDataStore.waitlistTimestamp != -1 && emailDataStore.inviteCode == null) {
            return WaitlistState.JoinedQueue
        }
        emailDataStore.inviteCode?.let {
            return WaitlistState.InBeta
        }
        return WaitlistState.NotJoinedQueue
    }

    override fun joinWaitlist(timestamp: Int, token: String) {
        if (emailDataStore.waitlistTimestamp == -1) { emailDataStore.waitlistTimestamp = timestamp }
        if (emailDataStore.waitlistToken == null) { emailDataStore.waitlistToken = token }
    }

    override fun getInviteCode(): String {
        return emailDataStore.inviteCode.orEmpty()
    }

    override suspend fun fetchInviteCode(): FetchCodeResult {
        Timber.i("Running email waitlist sync")
        val token = emailDataStore.waitlistToken
        val timestamp = emailDataStore.waitlistTimestamp
        if (doesCodeAlreadyExist()) return FetchCodeResult.CodeExisted
        return withContext(dispatcherProvider.io()) {
            try {
                Timber.i("Running waitlist status")
                val waitlistTimestamp = emailService.waitlistStatus().timestamp
                if (timestamp >= waitlistTimestamp && token != null) {
                    val inviteCode = emailService.getCode(token).code
                    Timber.i("Running waitlist getcode response is $inviteCode")
                    if (inviteCode.isNotEmpty()) {
                        emailDataStore.inviteCode = inviteCode
                        return@withContext FetchCodeResult.Code
                    }
                }
                return@withContext FetchCodeResult.NoCode
            } catch (e: Exception) {
                return@withContext FetchCodeResult.NoCode
            }
        }
    }

    override fun doesCodeAlreadyExist(): Boolean = emailDataStore.inviteCode != null

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

    sealed class FetchCodeResult {
        object Code : FetchCodeResult()
        object NoCode : FetchCodeResult()
        object CodeExisted : FetchCodeResult()
    }

    sealed class WaitlistState {
        object NotJoinedQueue : WaitlistState()
        object JoinedQueue : WaitlistState()
        object InBeta : WaitlistState()
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
