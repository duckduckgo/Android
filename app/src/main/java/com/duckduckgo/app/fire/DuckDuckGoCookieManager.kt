/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.fire

import android.webkit.CookieManager
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface DuckDuckGoCookieManager {
    suspend fun removeExternalCookies()
    fun flush()
}

class WebViewCookieManager(
    private val cookieManager: CookieManager,
    private val host: String,
    private val removeCookies: RemoveCookiesStrategy,
    private val dispatcher: DispatcherProvider
) : DuckDuckGoCookieManager {

    override suspend fun removeExternalCookies() {
        withContext(dispatcher.io()) {
            flush()
        }
        // We preserve DuckDuckGo cookies when the fire button is used to keep the previously DuckDuckGo Search Engine Result Pages settings set
        // by the user. Removing these cookies would reset them and have undesired consequences, i.e. changing the theme, default language, etc.
        // At DuckDuckGo, no cookies are used by default. If you have changed any settings, then cookies are used to store those changes.
        // However, in that case, they are not stored in a personally identifiable way. For example, the large size setting is stored as 's=l';
        // no unique identifier is in there. More info in https://duckduckgo.com/privacy
        val ddgCookies = getDuckDuckGoCookies()
        if (cookieManager.hasCookies()) {
            removeCookies.removeCookies()
            storeDuckDuckGoCookies(ddgCookies)
        }
        withContext(dispatcher.io()) {
            flush()
        }
    }

    private suspend fun storeDuckDuckGoCookies(cookies: List<String>) {
        cookies.forEach {
            val cookie = it.trim()
            Timber.d("Restoring DDB cookie: $cookie")
            storeCookie(cookie)
        }
    }

    private suspend fun storeCookie(cookie: String) {
        suspendCoroutine<Unit> { continuation ->
            cookieManager.setCookie(host, cookie) { success ->
                Timber.v("Cookie $cookie stored successfully: $success")
                continuation.resume(Unit)
            }
        }
    }

    private fun getDuckDuckGoCookies(): List<String> {
        return cookieManager.getCookie(host)?.split(";").orEmpty()
    }

    override fun flush() {
        cookieManager.flush()
    }
}
