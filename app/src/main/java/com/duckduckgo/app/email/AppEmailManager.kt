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
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

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

    override suspend fun getAlias(): String? = consumeAlias()

    init {
        // first call to isSignedIn() can be expensive and cause ANRs if done on main thread, so we do it on a background thread
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(isSignedIn())
        }
        emailSync.registerToRemoteChanges {
            refreshEmailState()
        }
    }

    override suspend fun isSignedIn(): Boolean {
        return !emailDataStore.getEmailToken().isNullOrBlank() && !emailDataStore.getEmailUsername().isNullOrBlank()
    }

    override suspend fun storeCredentials(
        token: String,
        username: String,
        cohort: String,
    ) {
        withContext(dispatcherProvider.io()) {
            emailDataStore.setCohort(cohort)
            emailDataStore.setEmailToken(token)
            emailDataStore.setEmailUsername(username)
            isSignedInStateFlow.emit(isSignedIn())
            generateNewAlias()
            pixel.fire(EMAIL_ENABLED)
            emailSync.onSettingChanged()
        }
    }

    override suspend fun signOut() {
        withContext(dispatcherProvider.io()) {
            emailDataStore.clearEmailData()
            isSignedInStateFlow.emit(false)
            pixel.fire(EMAIL_DISABLED)
            emailSync.onSettingChanged()
        }
    }

    override suspend fun getEmailAddress(): String? {
        return emailDataStore.getEmailUsername()?.let {
            "$it$DUCK_EMAIL_DOMAIN"
        }
    }

    override suspend fun getUserData(): String {
        return JSONObject().apply {
            put(TOKEN, emailDataStore.getEmailToken())
            put(USERNAME, emailDataStore.getEmailUsername())
            put(NEXT_ALIAS, emailDataStore.getNextAlias()?.replace(DUCK_EMAIL_DOMAIN, ""))
        }.toString()
    }

    override suspend fun getCohort(): String {
        val cohort = emailDataStore.getCohort()
        return if (cohort.isNullOrBlank()) UNKNOWN_COHORT else cohort
    }

    override suspend fun isEmailFeatureSupported(): Boolean = emailDataStore.canUseEncryption()

    override suspend fun getLastUsedDate(): String = emailDataStore.getLastUsedDate().orEmpty()

    override suspend fun setNewLastUsedDate() {
        val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("US/Eastern")
        }
        val date = formatter.format(Date())
        emailDataStore.setLastUsedDate(date)
    }

    override suspend fun getToken(): String? = emailDataStore.getEmailToken()

    private fun refreshEmailState() {
        Timber.i("Sync-Settings: refreshEmailState()")
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(isSignedIn())
            if (isSignedIn()) {
                generateNewAlias()
            } else {
                emailDataStore.clearEmailData()
            }
        }
    }

    private suspend fun consumeAlias(): String? {
        val alias = emailDataStore.getNextAlias()
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
        emailDataStore.getEmailToken()?.let { token ->
            runCatching {
                emailService.newAlias("Bearer $token")
            }.onSuccess { alias ->
                emailDataStore.setNextAlias(
                    if (alias.address.isBlank()) {
                        null
                    } else {
                        "${alias.address}$DUCK_EMAIL_DOMAIN"
                    },
                )
            }.onFailure {
                Timber.w(it, "Failed to fetch alias")
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

    private suspend fun EmailDataStore.clearEmailData() {
        setEmailToken(null)
        setEmailUsername(null)
        setNextAlias(null)
    }

    private suspend fun EmailDataStore.clearNextAlias() {
        setNextAlias(null)
    }

    override suspend fun featureStateParams(): Map<String, String> {
        return mapOf(PixelParameter.EMAIL to isSignedIn().toBinaryString())
    }
}
