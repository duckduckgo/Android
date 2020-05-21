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
import com.duckduckgo.app.browser.WebNavigationStateChange
import timber.log.Timber

class LoginDetectionDelegate {
    private var loginDetected: String? = null

    sealed class Event {
        data class NavigationEvent(val navigationStateChange: WebNavigationStateChange) : Event()
        object PageFinished : Event()
        data class LoginDetected(val url: String) : Event()
    }

    fun onEvent(event: Event): Boolean {
        Timber.i("LoginDetectionInterface $event")
        return when (event) {
            is Event.PageFinished -> {
                loginDetected = null
                false
            }
            is Event.NavigationEvent -> {
                handleNavigationStateChange(event)
            }
            is Event.LoginDetected -> {
                loginDetected = event.url
                false
            }
        }
    }

    private fun handleNavigationStateChange(event: Event.NavigationEvent): Boolean {
        return when (val navigationStateChange = event.navigationStateChange) {
            is WebNavigationStateChange.NewPage -> {
                return detectLogin(navigationStateChange.url)
            }
            is WebNavigationStateChange.PageCleared -> {
                loginDetected = null
                false
            }
            is WebNavigationStateChange.UrlUpdated -> {
                return detectLogin(navigationStateChange.url)
            }
            is WebNavigationStateChange.PageNavigationCleared -> {
                loginDetected = null
                false
            }
            is WebNavigationStateChange.Unchanged -> false
            is WebNavigationStateChange.Other -> false
        }
    }

    private fun detectLogin(url: String): Boolean {
        Timber.i("LoginDetectionInterface $loginDetected vs $url")
        if (loginDetected != null) {
            val loginURI = Uri.parse(loginDetected)
            val currentURI = Uri.parse(url)
            if (loginURI.host != currentURI.host || loginURI.path != currentURI.path) {
                //command.value = ShowFireproofWebSiteConfirmation(FireproofWebsiteEntity(loginURI.host))
                loginDetected = null
                return true
            }
        }
        return false
    }
}