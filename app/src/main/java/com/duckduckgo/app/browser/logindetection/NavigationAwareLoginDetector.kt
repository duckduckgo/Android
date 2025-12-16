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

package com.duckduckgo.app.browser.logindetection

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.browser.WebNavigationStateChange
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ValidUrl
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.getValidUrl
import kotlinx.coroutines.*
import logcat.LogPriority.VERBOSE
import logcat.logcat

interface NavigationAwareLoginDetector {
    val loginEventLiveData: LiveData<LoginDetected>
    fun onEvent(navigationEvent: NavigationEvent)
}

data class LoginDetected(
    val authLoginDomain: String,
    val forwardedToDomain: String,
)

sealed class NavigationEvent {
    sealed class UserAction : NavigationEvent() {
        data object NavigateForward : UserAction()
        data object NavigateBack : UserAction()
        data object NewQuerySubmitted : UserAction()
        data object Refresh : UserAction()
    }

    data class WebNavigationEvent(val navigationStateChange: WebNavigationStateChange) : NavigationEvent()
    data object PageFinished : NavigationEvent()
    data object GpcRedirect : NavigationEvent()
    data class LoginAttempt(val url: String) : NavigationEvent()
    data class Redirect(val url: String) : NavigationEvent()
}

class NextPageLoginDetection constructor(
    private val settingsDataStore: SettingsDataStore,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : NavigationAwareLoginDetector {

    override val loginEventLiveData = MutableLiveData<LoginDetected>()
    private var loginAttempt: ValidUrl? = null
    private var gpcRefreshed = false

    private var urlToCheck: String? = null
    /**
     * Using a LinkedHashSet to:
     * 1. Prevent duplicate entries (Set behavior)
     * 2. Maintain insertion order for potential eviction of oldest entries
     *
     * This prevents unbounded memory growth while maintaining login detection accuracy.
     */
    private var authDetectedHosts = linkedSetOf<String>()
    private var loginDetectionJob: Job? = null

    override fun onEvent(navigationEvent: NavigationEvent) {
        if (settingsDataStore.automaticFireproofSetting == AutomaticFireproofSetting.NEVER) return

        logcat(VERBOSE) { "LoginDetectionDelegate $navigationEvent" }
        return when (navigationEvent) {
            is NavigationEvent.PageFinished -> {
                logcat(VERBOSE) { "LoginDetectionDelegate schedule Login detection Job for $urlToCheck" }
                loginDetectionJob?.cancel()
                loginDetectionJob = scheduleLoginDetection()
            }
            is NavigationEvent.WebNavigationEvent -> {
                handleNavigationEvent(navigationEvent)
            }
            is NavigationEvent.LoginAttempt -> {
                saveLoginAttempt(navigationEvent)
            }
            is NavigationEvent.UserAction -> {
                discardLoginAttempt()
            }
            is NavigationEvent.Redirect -> {
                handleRedirect(navigationEvent)
            }
            is NavigationEvent.GpcRedirect -> {
                gpcRefreshed = true
            }
        }
    }

    private fun scheduleLoginDetection(): Job {
        // Ideally, we should be using a scope tied to the Activity/Fragment lifecycle instead of AppCoroutineScope.
        // AToW, it's not possible to inject such scope as dependency due to our single Component Dagger setup.
        return appCoroutineScope.launch(dispatcherProvider.main()) {
            delay(NAVIGATION_EVENT_GRACE_PERIOD)
            logcat(VERBOSE) { "LoginDetectionDelegate execute Login detection Job for $urlToCheck" }
            val loginUrlCandidate = urlToCheck
            if (loginUrlCandidate.isNullOrBlank()) {
                discardLoginAttempt()
            } else {
                when (val loginResult = detectLogin(loginUrlCandidate)) {
                    is LoginResult.AuthFlow -> {
                        logcat(VERBOSE) { "LoginDetectionDelegate AuthFlow" }
                        addAuthDetectedHost(loginResult.authLoginDomain)
                    }
                    is LoginResult.TwoFactorAuthFlow -> {
                        logcat(VERBOSE) { "LoginDetectionDelegate TwoFactorAuthFlow" }
                    }
                    is LoginResult.LoginDetected -> {
                        logcat { "LoginDetectionDelegate LoginDetected" }
                        loginEventLiveData.value = LoginDetected(loginResult.authLoginDomain, loginResult.forwardedToDomain)
                        loginAttempt = null
                    }
                    is LoginResult.Unknown -> {
                        logcat(VERBOSE) { "LoginDetectionDelegate Unknown" }
                        discardLoginAttempt()
                    }
                }
            }
        }
    }

    private fun saveLoginAttempt(navigationEvent: NavigationEvent.LoginAttempt) {
        logcat(VERBOSE) { "LoginDetectionDelegate saveLoginAttempt $navigationEvent" }
        loginAttempt = Uri.parse(navigationEvent.url).getValidUrl() ?: return
    }

    private fun discardLoginAttempt() {
        if (!gpcRefreshed) {
            gpcRefreshed = false
            logcat(VERBOSE) { "LoginDetectionDelegate discardLoginAttempt" }
            urlToCheck = null
            loginAttempt = null
        }
    }

    private fun handleNavigationEvent(navigationEvent: NavigationEvent.WebNavigationEvent) {
        return when (val navigationStateChange = navigationEvent.navigationStateChange) {
            is WebNavigationStateChange.NewPage -> {
                val baseHost = navigationStateChange.url.toUri().baseHost ?: return
                if (authDetectedHosts.any { baseHost.contains(it) }) {
                    addAuthDetectedHost(baseHost)
                } else {
                    authDetectedHosts.clear()
                }

                loginDetectionJob?.cancel()
                if (loginAttempt != null) {
                    urlToCheck = navigationStateChange.url
                }
                return
            }
            is WebNavigationStateChange.PageCleared -> discardLoginAttempt()
            is WebNavigationStateChange.UrlUpdated -> {
                loginDetectionJob?.cancel()
                if (loginAttempt != null) {
                    urlToCheck = navigationStateChange.url
                }
                return
            }
            is WebNavigationStateChange.PageNavigationCleared -> discardLoginAttempt()
            is WebNavigationStateChange.Unchanged -> {
            }
            is WebNavigationStateChange.Other -> {
            }
        }
    }

    private fun handleRedirect(navigationEvent: NavigationEvent.Redirect) {
        loginDetectionJob?.cancel()

        val validUrl = Uri.parse(navigationEvent.url).getValidUrl() ?: return
        if (validUrl.isOAuthUrl() || validUrl.isSSOUrl() || authDetectedHosts.any { validUrl.baseHost.contains(it) }) {
            addAuthDetectedHost(validUrl.baseHost)
            logcat { "LoginDetectionDelegate Auth domain added $authDetectedHosts" }
        }

        if (loginAttempt != null) {
            urlToCheck = navigationEvent.url
        }
    }

    private fun detectLogin(forwardedToUrl: String): LoginResult {
        val validLoginAttempt = loginAttempt ?: return LoginResult.Unknown
        val forwardedToUri = Uri.parse(forwardedToUrl).getValidUrl() ?: return LoginResult.Unknown

        if (authDetectedHosts.any { forwardedToUri.baseHost.contains(it) }) return LoginResult.AuthFlow(forwardedToUri.baseHost)

        if (forwardedToUri.isOAuthUrl() || forwardedToUri.isSSOUrl()) {
            return LoginResult.AuthFlow(forwardedToUri.baseHost)
        }

        if (forwardedToUri.is2FAUrl()) return LoginResult.TwoFactorAuthFlow(forwardedToUri.host)

        logcat { "LoginDetectionDelegate $validLoginAttempt vs $forwardedToUrl // $authDetectedHosts" }
        if (validLoginAttempt.host != forwardedToUri.host || validLoginAttempt.path != forwardedToUri.path) {
            return LoginResult.LoginDetected(validLoginAttempt.host, forwardedToUri.host)
        }

        return LoginResult.Unknown
    }

    private sealed class LoginResult {
        data class AuthFlow(val authLoginDomain: String) : LoginResult()
        data class TwoFactorAuthFlow(val loginDomain: String) : LoginResult()
        data class LoginDetected(
            val authLoginDomain: String,
            val forwardedToDomain: String,
        ) : LoginResult()

        data object Unknown : LoginResult()
    }

    /**
     * Adds a host to the auth detected hosts set with a maximum size limit.
     * When the maximum size is reached, the oldest entry is removed.
     */
    private fun addAuthDetectedHost(host: String) {
        // Remove oldest entries if we've reached the maximum size
        while (authDetectedHosts.size >= MAX_AUTH_DETECTED_HOSTS) {
            authDetectedHosts.iterator().next().let { oldest ->
                authDetectedHosts.remove(oldest)
            }
        }
        authDetectedHosts.add(host)
    }

    companion object {
        private const val NAVIGATION_EVENT_GRACE_PERIOD = 1_000L
        // Maximum number of auth detected hosts to track
        // This prevents unbounded memory growth during extended browsing sessions
        private const val MAX_AUTH_DETECTED_HOSTS = 50
    }
}
