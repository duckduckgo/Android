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

package com.duckduckgo.app.global.useourapp

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.browser.logindetection.WebNavigationEvent
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class UseOurAppDetector @Inject constructor(val userEventsStore: UserEventsStore) {

    fun isUseOurAppUrl(url: String?): Boolean {
        if (url == null) return false
        return isUseOurAppUrl(url.toUri())
    }

    fun allowLoginDetection(event: WebNavigationEvent): Boolean {
        val canShowFireproof = runBlocking {
            if (userEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED) == null) {
                false
            } else {
                (userEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN) == null)
            }
        }

        return if (canShowFireproof) {
            when (event) {
                is WebNavigationEvent.OnPageStarted -> isUseOurAppUrl(event.webView.url)
                is WebNavigationEvent.ShouldInterceptRequest -> isUseOurAppUrl(event.webView.url)
            }
        } else {
            false
        }
    }

    suspend fun registerIfFireproofSeenForTheFirstTime(url: String) {
        if (userEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN) == null && isUseOurAppUrl(url)) {
            userEventsStore.registerUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)
        }
    }

    private fun isUseOurAppUrl(uri: Uri): Boolean {
        return domainMatchesUrl(uri)
    }

    private fun domainMatchesUrl(uri: Uri): Boolean {
        return uri.baseHost?.contains(USE_OUR_APP_DOMAIN) ?: false
    }

    companion object {
        const val USE_OUR_APP_SHORTCUT_URL: String = "https://m.facebook.com/"
        const val USE_OUR_APP_SHORTCUT_TITLE: String = "Facebook"
        const val USE_OUR_APP_DOMAIN = "facebook.com"
        const val USE_OUR_APP_DOMAIN_QUERY = "%facebook.com%"
    }
}
