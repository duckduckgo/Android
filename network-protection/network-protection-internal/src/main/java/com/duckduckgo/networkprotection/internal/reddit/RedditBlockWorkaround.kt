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
import javax.inject.Inject
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat

private const val HTTPS_WWW_REDDIT_COM = ".reddit.com"
private const val REDDIT_SESSION_ = "reddit_session=;"

@ContributesMultibinding(AppScope::class)
class RedditBlockWorkaround @Inject constructor(
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(dispatcherProvider.io()) {
            runCatching {
                runCatching { CookieManager.getInstance() }.getOrNull()?.let { cookieManager ->
                    val redditCookies = cookieManager.getCookie(HTTPS_WWW_REDDIT_COM) ?: ""
                    val redditSessionCookies = redditCookies.split(";").filter { it.contains("reddit_session") }
                    if (networkProtectionState.isEnabled() && redditSessionCookies.isEmpty()) {
                        // if the VPN is enabled and there's no reddit_session cookie we just add a fake one
                        // when the user logs into reddit, the fake reddit_session cookie is replaced automatically by the correct one
                        // when the user logs out, the reddit_session cookie is cleared
                        cookieManager.setCookie(HTTPS_WWW_REDDIT_COM, REDDIT_SESSION_)
                        cookieManager.flush()
                    }
                }
            }.onFailure {
                logcat { "Reddit workaround error: ${it.asLog()}" }
            }
        }
    }
}
