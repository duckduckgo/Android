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
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.browser.WebNavigationStateChange
import timber.log.Timber

class LoginDetectionDelegate {

    private var loginAttempt: Event.LoginAttempt? = null
    var loginEventLiveData = MutableLiveData<LoginDetected>()

    data class LoginDetected(val authLoginDomain: String, val forwardedToDomain: String)

    sealed class Event {
        sealed class UserAction : Event() {
            object NavigateForward : UserAction()
            object NavigateBack : UserAction()
            object NewQuerySubmitted : UserAction()
            object Refresh : UserAction()
        }

        data class WebNavigationEvent(val navigationStateChange: WebNavigationStateChange) : Event()
        object PageFinished : Event()
        data class LoginAttempt(val url: String) : Event()
    }

    fun onEvent(event: Event) {
        Timber.i("LoginDetectionDelegate $event")
        return when (event) {
            is Event.PageFinished -> {
                discardLoginAttempt()
            }
            is Event.WebNavigationEvent -> {
                handleNavigationEvent(event)
            }
            is Event.LoginAttempt -> {
                saveLoginAttempt(event)
            }
            is Event.UserAction -> {
                discardLoginAttempt()
            }
        }
    }

    private fun saveLoginAttempt(event: Event.LoginAttempt) {
        loginAttempt = event
    }

    private fun discardLoginAttempt() {
        loginAttempt = null
    }

    private fun handleNavigationEvent(event: Event.WebNavigationEvent) {
        return when (val navigationStateChange = event.navigationStateChange) {
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

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun detectLogin(forwardedToUrl: String) {
        val loginAttemptEvent = loginAttempt ?: return
        val loginURI = Uri.parse(loginAttemptEvent.url).takeUnless { it.host.isNullOrBlank() } ?: return
        val forwardedToUri = Uri.parse(forwardedToUrl).takeUnless { it.host.isNullOrBlank() } ?: return

        Timber.i("LoginDetectionDelegate ${loginAttemptEvent.url} vs $forwardedToUrl")
        if (loginURI.host != forwardedToUri.host || loginURI.path != forwardedToUri.path) {
            loginEventLiveData.value = LoginDetected(loginURI.host, forwardedToUri.host)
            loginAttempt = null
        }
    }
}
