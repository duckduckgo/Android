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
import timber.log.Timber
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

    private var loginAttempt: NavigationEvent.LoginAttempt? = null
    override val loginEventLiveData = MutableLiveData<LoginDetected>()

    override fun onEvent(navigationEvent: NavigationEvent) {
        Timber.i("LoginDetectionDelegate $navigationEvent")
        return when (navigationEvent) {
            is NavigationEvent.PageFinished -> {
                discardLoginAttempt()
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
        loginAttempt = navigationEvent
    }

    private fun discardLoginAttempt() {
        loginAttempt = null
    }

    private fun handleNavigationEvent(navigationEvent: NavigationEvent.WebNavigationEvent) {
        return when (val navigationStateChange = navigationEvent.navigationStateChange) {
            is WebNavigationStateChange.NewPage -> {
                detectLogin(navigationStateChange.url)
            }
            is WebNavigationStateChange.PageCleared -> discardLoginAttempt()
            is WebNavigationStateChange.UrlUpdated -> {
                detectLogin(navigationStateChange.url)
            }
            is WebNavigationStateChange.PageNavigationCleared -> discardLoginAttempt()
            is WebNavigationStateChange.Unchanged -> {
            }
            is WebNavigationStateChange.Other -> {
            }
        }
    }

    private fun detectLogin(forwardedToUrl: String) {
        val loginAttemptEvent = loginAttempt ?: return
        val loginURI = Uri.parse(loginAttemptEvent.url).takeUnless { it.host.isNullOrBlank() } ?: return
        val forwardedToUri = Uri.parse(forwardedToUrl).takeUnless { it.host.isNullOrBlank() } ?: return

        Timber.i("LoginDetectionDelegate ${loginAttemptEvent.url} vs $forwardedToUrl")
        if (loginURI.host != forwardedToUri.host || loginURI.path != forwardedToUri.path) {
            val loginUriHost = loginURI.host
            val forwardedToUriHost = forwardedToUri.host

            if (loginUriHost != null && forwardedToUriHost != null) {
                loginEventLiveData.value = LoginDetected(loginUriHost, forwardedToUriHost)
            }

            loginAttempt = null
        }
    }
}
