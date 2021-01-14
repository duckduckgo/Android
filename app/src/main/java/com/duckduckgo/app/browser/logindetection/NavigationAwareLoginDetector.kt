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
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.regex.Pattern
import javax.inject.Inject

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
    data class Redirect(val url: String): NavigationEvent()
}

class NextPageLoginDetection @Inject constructor() : NavigationAwareLoginDetector {

    override val loginEventLiveData = MutableLiveData<LoginDetected>()
    private var loginAttempt: ValidUrl? = null
    private var gpcRefreshed = false

    private var urlToCheck: String? = null
    private var authDetectedHosts = mutableListOf<String>()
    private var loginDetectionJob: Job? = null

    override fun onEvent(navigationEvent: NavigationEvent) {
        Timber.i("LoginDetectionDelegate $navigationEvent")
        return when (navigationEvent) {
            is NavigationEvent.PageFinished -> {
                Timber.i("LoginDetectionDelegate schedule Login detection Job for $urlToCheck")
                loginDetectionJob?.cancel()
                loginDetectionJob = GlobalScope.launch(Dispatchers.Main) {
                    delay(1000)
                    Timber.i("LoginDetectionDelegate execute Login detection Job for $urlToCheck")
                    if (urlToCheck.isNullOrBlank()) {
                        discardLoginAttempt()
                    } else {
                        when (val detectLogin = detectLogin(urlToCheck!!)) {
                            is LoginResult.Unknown -> {
                                Timber.i("LoginDetectionDelegate Unknown")
                                discardLoginAttempt()
                            }
                            is LoginResult.AuthFlow -> {
                                Timber.i("LoginDetectionDelegate AuthFlow")
                                authDetectedHosts.add(detectLogin.authLoginDomain)
                                Timber.i("LoginDetectionDelegate Auth domain added $authDetectedHosts")
                            }
                            is LoginResult.LoginDetected -> {
                                loginEventLiveData.value = LoginDetected(detectLogin.authLoginDomain, detectLogin.forwardedToDomain)
                                loginAttempt = null
                            }
                            is LoginResult.TwoFactorAuthFlow -> {
                                Timber.i("LoginDetectionDelegate TwoFactorAuthFlow")
                            }
                        }
                    }
                }
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
                    authDetectedHosts.add(validUrl.host.removePrefix("www."))
                    Timber.i("LoginDetectionDelegate Auth domain added $authDetectedHosts")
                }
                return
            }
            is NavigationEvent.GpcRedirect -> {
                gpcRefreshed = true
            }
        }
    }

    private fun saveLoginAttempt(navigationEvent: NavigationEvent.LoginAttempt) {
        Timber.i("LoginDetectionDelegate saveLoginAttempt $navigationEvent")
        loginAttempt = Uri.parse(navigationEvent.url).getValidUrl() ?: return
    }

    private fun discardLoginAttempt() {
        if (!gpcRefreshed) {
            gpcRefreshed = false
            Timber.i("LoginDetectionDelegate discardLoginAttempt")
            urlToCheck = null
            loginAttempt = null
        }
    }

    private fun handleNavigationEvent(navigationEvent: NavigationEvent.WebNavigationEvent) {
        return when (val navigationStateChange = navigationEvent.navigationStateChange) {
            is WebNavigationStateChange.NewPage -> {
                val baseHost = navigationStateChange.url.toUri().baseHost
                if(authDetectedHosts.firstOrNull { baseHost?.contains(it) == true } == null) {
                    Timber.i("LoginDetectionDelegate AuthFlow Cleared")
                    authDetectedHosts.clear()
                } else {
                    authDetectedHosts.add(baseHost!!)
                }
                cancelLoginJob()
                if (loginAttempt != null) {
                    //detectLogin(navigationStateChange.url)
                    urlToCheck = navigationStateChange.url
                    Timber.i("LoginDetectionDelegate urlToCheck $urlToCheck")
                }
                return
            }
            is WebNavigationStateChange.PageCleared -> discardLoginAttempt()
            is WebNavigationStateChange.UrlUpdated -> {
                cancelLoginJob()
                if (loginAttempt != null) {
                    //detectLogin(navigationStateChange.url)
                    urlToCheck = navigationStateChange.url
                    Timber.i("LoginDetectionDelegate urlToCheck $urlToCheck")
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

    private fun cancelLoginJob() {
        Timber.i("LoginDetectionDelegate cancelled login detection job")
        loginDetectionJob?.cancel()
    }

    sealed class LoginResult {
        data class AuthFlow(val authLoginDomain: String) : LoginResult()
        data class TwoFactorAuthFlow(val loginDomain: String) : LoginResult()
        data class LoginDetected(val authLoginDomain: String, val forwardedToDomain: String) : LoginResult()
        object Unknown : LoginResult()
    }

    private fun detectLogin(forwardedToUrl: String): LoginResult {
        val validLoginAttempt = loginAttempt ?: return LoginResult.Unknown
        val forwardedToUri = Uri.parse(forwardedToUrl).getValidUrl() ?: return LoginResult.Unknown

        if(authDetectedHosts.firstOrNull { forwardedToUri.host.contains(it) } != null) return  LoginResult.AuthFlow(forwardedToUri.host)

        if (forwardedToUri.isOAuthUrl() || authDetectedHosts.contains(forwardedToUri.host)){
            return LoginResult.AuthFlow(forwardedToUri.host)
        }

        if(forwardedToUri.is2FAUrl()) return LoginResult.TwoFactorAuthFlow(forwardedToUri.host)

        Timber.i("LoginDetectionDelegate $validLoginAttempt vs $forwardedToUrl // $authDetectedHosts")
        if (validLoginAttempt.host != forwardedToUri.host || validLoginAttempt.path != forwardedToUri.path) {
            Timber.i("LoginDetectionDelegate LoginDetected*************************")
            return LoginResult.LoginDetected(validLoginAttempt.host, forwardedToUri.host)
        }

        return LoginResult.Unknown
    }
}