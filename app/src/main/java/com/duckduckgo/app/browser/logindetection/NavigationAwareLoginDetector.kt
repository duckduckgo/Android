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
    object GpcRedirect : NavigationEvent()
    data class LoginAttempt(val url: String) : NavigationEvent()
}

class NextPageLoginDetection @Inject constructor() : NavigationAwareLoginDetector {

    override val loginEventLiveData = MutableLiveData<LoginDetected>()
    private var loginAttempt: ValidUrl? = null
    private var gpcRefreshed = false

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
            is NavigationEvent.GpcRedirect -> {
                gpcRefreshed = true
            }
        }
    }

    private fun saveLoginAttempt(navigationEvent: NavigationEvent.LoginAttempt) {
        loginAttempt = Uri.parse(navigationEvent.url).getValidUrl() ?: return
    }

    private fun discardLoginAttempt() {
        if (!gpcRefreshed) {
            gpcRefreshed = false
            loginAttempt = null
        }
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
            is WebNavigationStateChange.ProgressChanged -> {
            }
        }
    }

    private fun detectLogin(forwardedToUrl: String) {
        val validLoginAttempt = loginAttempt ?: return
        val forwardedToUri = Uri.parse(forwardedToUrl).getValidUrl() ?: return

        Timber.i("LoginDetectionDelegate $validLoginAttempt vs $forwardedToUrl")
        if (validLoginAttempt.host != forwardedToUri.host || validLoginAttempt.path != forwardedToUri.path) {
            loginEventLiveData.value = LoginDetected(validLoginAttempt.host, forwardedToUri.host)
            loginAttempt = null
        }
    }

    private fun Uri.getValidUrl(): ValidUrl? {
        val validHost = host ?: return null
        return ValidUrl(validHost, path)
    }

    private data class ValidUrl(val host: String, val path: String?)
}
