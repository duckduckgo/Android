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
import com.duckduckgo.app.global.baseHost
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

class AuthUrlDetector {
    private var signinPages = mapOf<String, Set<Pattern>>(
        Pair(
            "accounts.google.com", setOf(Pattern.compile("signin/v\\d.*/challenge"))
        ),
        Pair(
            "sso", setOf(Pattern.compile("duosecurity/getduo"))
        ),
        Pair(
            "amazon.com", setOf(Pattern.compile("ap/challenge"), Pattern.compile("ap/cvf/approval"))
        )
    )

    private var ssoProvider = mapOf<String, Set<Pattern>>(
        Pair(
            "sso", setOf(Pattern.compile("saml2/idp/SSOService"))
        )
    )

    private var authenticationDetector = mapOf<String, Set<Pattern>>(
        Pair(
            "accounts.google.com", setOf(Pattern.compile("o/oauth2/auth"), Pattern.compile("o/oauth2/v\\d.*/auth"))
        ),
        Pair(
            "appleid.apple.com", setOf(Pattern.compile("auth/authorize"))
        ),
        Pair(
            "amazon.com", setOf(Pattern.compile("ap/oa"))
        ),
        Pair(
            "auth.atlassian.com", setOf(Pattern.compile("authorize"))
        ),
        Pair(
            "facebook.com", setOf(Pattern.compile("/v\\d.*\\/dialog/oauth"), Pattern.compile("dialog/oauth"))
        ),
        Pair(
            "login.microsoftonline.com", setOf(Pattern.compile("common/oauth2/authorize"), Pattern.compile("common/oauth2/v2.0/authorize"))
        ),
        Pair(
            "linkedin.com", setOf(Pattern.compile("oauth/v\\d.*/authorization"))
        ),
        Pair(
            "github.com", setOf(Pattern.compile("login/oauth/authorize"))
        ),
        Pair(
            "api.twitter.com", setOf(Pattern.compile("oauth/authenticate"), Pattern.compile("oauth/authorize"))
        ),
        Pair(
            "duosecurity.com", setOf(Pattern.compile("oauth/v\\d.*/authorize"))
        )
    )

    fun isAuthUrl(forwardedToUri: ValidUrl): Boolean {
        authenticationDetector.keys
            .firstOrNull { forwardedToUri.host.contains(it) }
            ?.let { authenticationDetector[it] }
            ?.forEach {
                if (it.matcher(forwardedToUri.path.orEmpty()).find()) {
                    return true
                }
            }

        return false
    }

    fun is2FAStep(forwardedToUri: ValidUrl): Boolean {
        signinPages.keys
            .firstOrNull { forwardedToUri.host.contains(it) }
            ?.let { signinPages[it] }
            ?.forEach {
                if (it.matcher(forwardedToUri.path.orEmpty()).find()) {
                    return true
                }
            }

        return false
    }

    fun isSSOPage(forwardedToUri: ValidUrl): Boolean {
        ssoProvider.keys
            .firstOrNull { forwardedToUri.host.contains(it) }
            ?.let { ssoProvider[it] }
            ?.forEach {
                if (it.matcher(forwardedToUri.path.orEmpty()).find()) {
                    return true
                }
            }

        return false
    }
}

class NextPageLoginDetection @Inject constructor() : NavigationAwareLoginDetector {

    override val loginEventLiveData = MutableLiveData<LoginDetected>()
    private var loginAttempt: ValidUrl? = null
    private var gpcRefreshed = false

    private var urlToCheck: String? = null
    private var authDetectedHosts = mutableListOf<String>()
    private var loginDetectionJob: Job? = null
    private val authDetector: AuthUrlDetector = AuthUrlDetector()

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
                if (authDetector.isAuthUrl(validUrl) || authDetector.isSSOPage(validUrl)) {
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

        if (authDetector.isAuthUrl(forwardedToUri) || authDetectedHosts.contains(forwardedToUri.host)){
            return LoginResult.AuthFlow(forwardedToUri.host)
        }

        if(authDetector.is2FAStep(forwardedToUri)) return LoginResult.TwoFactorAuthFlow(forwardedToUri.host)

        Timber.i("LoginDetectionDelegate $validLoginAttempt vs $forwardedToUrl // $authDetectedHosts")
        if (validLoginAttempt.host != forwardedToUri.host || validLoginAttempt.path != forwardedToUri.path) {
            Timber.i("LoginDetectionDelegate LoginDetected*************************")
            return LoginResult.LoginDetected(validLoginAttempt.host, forwardedToUri.host)
        }

        return LoginResult.Unknown
    }
}

fun Uri.getValidUrl(): ValidUrl? {
    val validHost = host ?: return null
    return ValidUrl(validHost, path)
}

data class ValidUrl(
    val host: String,
    val path: String?
)
