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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.browser.WebNavigationStateChange
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
    data class LoginAttempt(val url: String) : NavigationEvent()
}

class NextPageLoginDetection @Inject constructor() : NavigationAwareLoginDetector {

    override val loginEventLiveData = MutableLiveData<LoginDetected>()
    private var loginAttempt: ValidUrl? = null

    private var urlToCheck: String? = null
    private var loginDetectionJob: Job? = null
    private var authenticationDetector = mapOf<String, Set<Pattern>>(
        Pair(
            "accounts.google.com", setOf(Pattern.compile("oauth2/v\\d/"), Pattern.compile("signin/v\\d/challenge"))
        ),
        Pair(
            "appleid.apple.com", setOf(Pattern.compile("auth/auhtorize"))
        ),
        Pair(
            "facebook.com", setOf(Pattern.compile("\\/v\\d.*\\/oauth"))
        ),
        Pair(
            "sso", setOf(Pattern.compile("duosecurity/getduo"))
        )
    )

    override fun onEvent(navigationEvent: NavigationEvent) {
        Timber.i("LoginDetectionDelegate $navigationEvent")
        return when (navigationEvent) {
            is NavigationEvent.PageFinished -> {
                Timber.i("LoginDetectionDelegate execute new Login detection Job for $urlToCheck")

                loginDetectionJob = GlobalScope.launch(Dispatchers.Main) {
                    delay(1000)
                    if (urlToCheck.isNullOrBlank()) {
                        discardLoginAttempt()
                    } else {
                        when (val detectLogin = detectLogin(urlToCheck!!)) {
                            is LoginResult.Unknown -> {
                                discardLoginAttempt()
                            }
                            is LoginResult.AuthFlow -> {
                            }
                            is LoginResult.LoginDetected -> {
                                loginEventLiveData.value = LoginDetected(detectLogin.authLoginDomain, detectLogin.forwardedToDomain)
                                loginAttempt = null
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
        }
    }

    private fun saveLoginAttempt(navigationEvent: NavigationEvent.LoginAttempt) {
        loginAttempt = Uri.parse(navigationEvent.url).getValidUrl() ?: return
    }

    private fun discardLoginAttempt() {
        urlToCheck = null
        loginAttempt = null
    }

    private fun handleNavigationEvent(navigationEvent: NavigationEvent.WebNavigationEvent) {
        return when (val navigationStateChange = navigationEvent.navigationStateChange) {
            is WebNavigationStateChange.NewPage -> {
                cancelLoginJob()
                if (loginAttempt != null) {
                    //detectLogin(navigationStateChange.url)
                    urlToCheck = navigationStateChange.url
                }
                return
            }
            is WebNavigationStateChange.PageCleared -> discardLoginAttempt()
            is WebNavigationStateChange.UrlUpdated -> {
                cancelLoginJob()
                if (loginAttempt != null) {
                    //detectLogin(navigationStateChange.url)
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

    private fun cancelLoginJob() {
        Timber.i("LoginDetectionDelegate cancelled login detection job")
        loginDetectionJob?.cancel()
    }

    sealed class LoginResult {
        object AuthFlow : LoginResult()
        data class LoginDetected(val authLoginDomain: String, val forwardedToDomain: String) : LoginResult()
        object Unknown : LoginResult()
    }

    private fun detectLogin(forwardedToUrl: String): LoginResult {
        val validLoginAttempt = loginAttempt ?: return LoginResult.Unknown
        val forwardedToUri = Uri.parse(forwardedToUrl).getValidUrl() ?: return LoginResult.Unknown

        authenticationDetector.keys
            .firstOrNull { forwardedToUri.host.contains(it) }
            ?.let { authenticationDetector[it] }
            ?.forEach {
                if (it.matcher(forwardedToUri.path.orEmpty()).find()) {
                    return LoginResult.AuthFlow
                }
            }

        Timber.i("LoginDetectionDelegate $validLoginAttempt vs $forwardedToUrl")
        if (validLoginAttempt.host != forwardedToUri.host || validLoginAttempt.path != forwardedToUri.path) {
            Timber.i("LoginDetectionDelegate LoginDetected")
            return LoginResult.LoginDetected(validLoginAttempt.host, forwardedToUri.host)
        }

        return LoginResult.Unknown
    }

    private fun Uri.getValidUrl(): ValidUrl? {
        val validHost = host ?: return null
        return ValidUrl(validHost, path)
    }

    private data class ValidUrl(
        val host: String,
        val path: String?
    )
}
