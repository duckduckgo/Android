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
import com.duckduckgo.app.global.ValidUrl
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.getValidUrl
import com.duckduckgo.app.settings.db.SettingsDataStore
import kotlinx.coroutines.*
import timber.log.Timber

interface NavigationAwareLoginDetector {
    val loginEventLiveData: LiveData<LoginDetected>
    fun onEvent(navigationEvent: NavigationEvent)
}

data class LoginDetected(val authLoginDomain: String, val forwardedToDomain: String)

sealed class NavigationEvent {
    sealed class UserAction : NavigationEvent() {
        object NavigateForward : UserAction()
        object NavigateBack : UserAction()
        object NewQuerySubmitted : UserAction()
        object Refresh : UserAction()
    }

    data class WebNavigationEvent(val navigationStateChange: WebNavigationStateChange) : NavigationEvent()
    object PageFinished : NavigationEvent()
    object GpcRedirect : NavigationEvent()
    data class LoginAttempt(val url: String) : NavigationEvent()
    data class Redirect(val url: String) : NavigationEvent()
}

class NextPageLoginDetection constructor(private val settingsDataStore: SettingsDataStore) : NavigationAwareLoginDetector {

    override val loginEventLiveData = MutableLiveData<LoginDetected>()
    private var loginAttempt: ValidUrl? = null
    private var gpcRefreshed = false

    private var urlToCheck: String? = null
    private var authDetectedHosts = mutableListOf<String>()
    private var loginDetectionJob: Job? = null

    override fun onEvent(navigationEvent: NavigationEvent) {
        if (!settingsDataStore.appLoginDetection) return

        Timber.v("LoginDetectionDelegate $navigationEvent")
        return when (navigationEvent) {
            is NavigationEvent.PageFinished -> {
                Timber.v("LoginDetectionDelegate schedule Login detection Job for $urlToCheck")
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
                loginDetectionJob?.cancel()
                val validUrl = Uri.parse(navigationEvent.url).getValidUrl() ?: return
                if (validUrl.isOAuthUrl() || validUrl.isSSOUrl()) {
                    authDetectedHosts.add(validUrl.baseHost)
                    Timber.d("LoginDetectionDelegate Auth domain added $authDetectedHosts")
                }
                return
            }
            is NavigationEvent.GpcRedirect -> {
                gpcRefreshed = true
            }
        }
    }

    private fun scheduleLoginDetection(): Job {
        return GlobalScope.launch(Dispatchers.Main) {
            delay(1000)
            Timber.v("LoginDetectionDelegate execute Login detection Job for $urlToCheck")
            val loginUrlCandidate = urlToCheck
            if (loginUrlCandidate.isNullOrBlank()) {
                discardLoginAttempt()
            } else {
                when (val loginResult = detectLogin(loginUrlCandidate)) {
                    is LoginResult.AuthFlow -> {
                        Timber.v("LoginDetectionDelegate AuthFlow")
                        authDetectedHosts.add(loginResult.authLoginDomain)
                    }
                    is LoginResult.TwoFactorAuthFlow -> {
                        Timber.v("LoginDetectionDelegate TwoFactorAuthFlow")
                    }
                    is LoginResult.LoginDetected -> {
                        loginEventLiveData.value = LoginDetected(loginResult.authLoginDomain, loginResult.forwardedToDomain)
                        loginAttempt = null
                    }
                    is LoginResult.Unknown -> {
                        discardLoginAttempt()
                    }
                }
            }
        }
    }

    private fun saveLoginAttempt(navigationEvent: NavigationEvent.LoginAttempt) {
        Timber.v("LoginDetectionDelegate saveLoginAttempt $navigationEvent")
        loginAttempt = Uri.parse(navigationEvent.url).getValidUrl() ?: return
    }

    private fun discardLoginAttempt() {
        if (!gpcRefreshed) {
            gpcRefreshed = false
            Timber.v("LoginDetectionDelegate discardLoginAttempt")
            urlToCheck = null
            loginAttempt = null
        }
    }

    private fun handleNavigationEvent(navigationEvent: NavigationEvent.WebNavigationEvent) {
        return when (val navigationStateChange = navigationEvent.navigationStateChange) {
            is WebNavigationStateChange.NewPage -> {
                val baseHost = navigationStateChange.url.toUri().baseHost ?: return
                if (authDetectedHosts.any { baseHost.contains(it) }) {
                    authDetectedHosts.add(baseHost)
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

    private fun detectLogin(forwardedToUrl: String): LoginResult {
        val validLoginAttempt = loginAttempt ?: return LoginResult.Unknown
        val forwardedToUri = Uri.parse(forwardedToUrl).getValidUrl() ?: return LoginResult.Unknown

        if (authDetectedHosts.any { forwardedToUri.baseHost.contains(it) }) return LoginResult.AuthFlow(forwardedToUri.baseHost)

        if (forwardedToUri.isOAuthUrl() || forwardedToUri.isSSOUrl()) {
            return LoginResult.AuthFlow(forwardedToUri.baseHost)
        }

        if (forwardedToUri.is2FAUrl()) return LoginResult.TwoFactorAuthFlow(forwardedToUri.host)

        Timber.d("LoginDetectionDelegate $validLoginAttempt vs $forwardedToUrl // $authDetectedHosts")
        if (validLoginAttempt.host != forwardedToUri.host || validLoginAttempt.path != forwardedToUri.path) {
            Timber.i("LoginDetectionDelegate LoginDetected")
            return LoginResult.LoginDetected(validLoginAttempt.host, forwardedToUri.host)
        }

        return LoginResult.Unknown
    }

    private sealed class LoginResult {
        data class AuthFlow(val authLoginDomain: String) : LoginResult()
        data class TwoFactorAuthFlow(val loginDomain: String) : LoginResult()
        data class LoginDetected(val authLoginDomain: String, val forwardedToDomain: String) : LoginResult()
        object Unknown : LoginResult()
    }
}
