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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.email.sync.*
import com.duckduckgo.app.pixels.AppPixelName.EMAIL_DISABLED
import com.duckduckgo.app.pixels.AppPixelName.EMAIL_ENABLED
import com.duckduckgo.app.statistics.api.BrowserFeatureStateReporterPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toBinaryString
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.*

@ContributesMultibinding(scope = AppScope::class, boundType = BrowserFeatureStateReporterPlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = EmailManager::class)
@SingleInstanceIn(AppScope::class)
class AppEmailManager @Inject constructor(
    private val emailService: EmailService,
    private val emailDataStore: EmailDataStore,
    private val emailSync: EmailSync,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixel: Pixel,
) : EmailManager, BrowserFeatureStateReporterPlugin {

    private val isSignedInStateFlow = MutableStateFlow(false)
    override fun signedInFlow(): StateFlow<Boolean> = isSignedInStateFlow.asStateFlow()

    override fun getAlias(): String? = consumeAlias()

    init {
        // first call to isSignedIn() can be expensive and cause ANRs if done on main thread, so we do it on a background thread
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(isSignedIn())
        }
        emailSync.registerToRemoteChanges {
            refreshEmailState()
        }
    }

    override fun isSignedIn(): Boolean {
        return !emailDataStore.emailToken.isNullOrBlank() && !emailDataStore.emailUsername.isNullOrBlank()
    }

    override fun storeCredentials(
        token: String,
        username: String,
        cohort: String,
    ) {
        emailDataStore.cohort = cohort
        emailDataStore.emailToken = token
        emailDataStore.emailUsername = username
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(isSignedIn())
            generateNewAlias()
            pixel.fire(EMAIL_ENABLED)
            emailSync.onSettingChanged()
        }
    }

    override fun signOut() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            emailDataStore.clearEmailData()
            isSignedInStateFlow.emit(false)
            pixel.fire(EMAIL_DISABLED)
            emailSync.onSettingChanged()
        }
    }

    override fun getEmailAddress(): String? {
        return emailDataStore.emailUsername?.let {
            "$it$DUCK_EMAIL_DOMAIN"
        }
    }

    override fun getUserData(): String {
        return JSONObject().apply {
            put(TOKEN, emailDataStore.emailToken)
            put(USERNAME, emailDataStore.emailUsername)
            put(NEXT_ALIAS, emailDataStore.nextAlias?.replace(DUCK_EMAIL_DOMAIN, ""))
        }.toString()
    }

    override fun getCohort(): String {
        val cohort = emailDataStore.cohort
        return if (cohort.isNullOrBlank()) UNKNOWN_COHORT else cohort
    }

    override fun isEmailFeatureSupported(): Boolean = emailDataStore.canUseEncryption()

    override fun getLastUsedDate(): String = emailDataStore.lastUsedDate.orEmpty()

    override fun setNewLastUsedDate() {
        val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("US/Eastern")
        }
        val date = formatter.format(Date())
        emailDataStore.lastUsedDate = date
    }

    override fun getToken(): String? = emailDataStore.emailToken

    private fun refreshEmailState() {
        logcat(INFO) { "Sync-Settings: refreshEmailState()" }
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(isSignedIn())
            if (isSignedIn()) {
                generateNewAlias()
            } else {
                emailDataStore.clearEmailData()
            }
        }
    }

    private fun consumeAlias(): String? {
        val alias = emailDataStore.nextAlias
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
                logcat(WARN) { "Failed to fetch alias: ${it.asLog()}" }
            }
        }
    }

    companion object {
        const val DUCK_EMAIL_DOMAIN = "@duck.com"
        const val UNKNOWN_COHORT = "unknown"
        const val TOKEN = "token"
        const val USERNAME = "userName"
        const val NEXT_ALIAS = "nextAlias"
    }

    private fun EmailDataStore.clearEmailData() {
        emailToken = null
        emailUsername = null
        nextAlias = null
    }

    private fun EmailDataStore.clearNextAlias() {
        nextAlias = null
    }

    override fun featureStateParams(): Map<String, String> {
        return mapOf(PixelParameter.EMAIL to isSignedIn().toBinaryString())
    }
}
