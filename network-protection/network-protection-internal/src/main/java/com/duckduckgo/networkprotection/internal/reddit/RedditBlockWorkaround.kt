/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.internal.reddit

import android.webkit.CookieManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val HTTPS_WWW_REDDIT_COM = ".reddit.com"
private const val REDDIT_SESSION_ = "reddit_session=;"

@ContributesMultibinding(AppScope::class)
class RedditBlockWorkaround @Inject constructor(
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(dispatcherProvider.io()) {
            runCatching { CookieManager.getInstance() }.getOrNull()?.let { cookieManager ->
                val redditCookies = cookieManager.getCookie(HTTPS_WWW_REDDIT_COM)
                val redditSessionCookies = redditCookies.split(";").filter { it.contains("reddit_session") }
                if (redditSessionCookies.size > 1) {
                    // remove potential fake cookie
                    val finalCookie = redditSessionCookies.firstOrNull { it.substringAfter("=", missingDelimiterValue = "").isNotEmpty() }
                    finalCookie?.let {
                        cookieManager.setCookie(HTTPS_WWW_REDDIT_COM, it)
                        cookieManager.flush()
                    }
                } else if (redditSessionCookies.size == 1) {
                    if (!networkProtectionState.isEnabled()) {
                        val finalCookie = redditSessionCookies.firstOrNull { it.substringAfter("=", missingDelimiterValue = "").isNotEmpty() } ?: ""
                        cookieManager.setCookie(HTTPS_WWW_REDDIT_COM, finalCookie)
                        cookieManager.flush()
                    }
                } else {
                    if (networkProtectionState.isEnabled()) {
                        cookieManager.setCookie(HTTPS_WWW_REDDIT_COM, REDDIT_SESSION_)
                        cookieManager.flush()
                    }
                }
            }
        }
    }
}
