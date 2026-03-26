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

package com.duckduckgo.networkprotection.impl.reddit

import android.webkit.CookieManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.launch
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import javax.inject.Qualifier

private const val HTTPS_WWW_REDDIT_COM = ".reddit.com"
private const val REDDIT_SESSION_ = "reddit_session=;"
private const val REDDIT_SESSION_EXPIRED_ = "reddit_session=; Expires=Wed, 21 Oct 2015 07:28:00 GMT" // random date

@ContributesMultibinding(AppScope::class)
class RedditBlockWorkaround @Inject constructor(
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
    @InternalApi private val cookieManager: CookieManagerWrapper,
) : MainProcessLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(dispatcherProvider.io()) {
            addRedditEmptyCookie()
        }
    }
    override fun onPause(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(dispatcherProvider.io()) {
            removeRedditEmptyCookie()
        }
    }

    private suspend fun addRedditEmptyCookie() {
        runCatching {
            val redditCookies = cookieManager.getCookie(HTTPS_WWW_REDDIT_COM) ?: ""
            val redditSessionCookies = redditCookies.split(";").filter { it.contains("reddit_session") }
            if (networkProtectionState.isEnabled() && redditSessionCookies.isEmpty()) {
                // if the VPN is enabled and there's no reddit_session cookie we just add a fake one
                // when the user logs into reddit, the fake reddit_session cookie is replaced automatically by the correct one
                // when the user logs out, the reddit_session cookie is cleared
                cookieManager.setCookie(HTTPS_WWW_REDDIT_COM, REDDIT_SESSION_)
            } else {
                removeRedditEmptyCookie()
            }
        }.onFailure {
            logcat { "Reddit workaround error: ${it.asLog()}" }
        }
    }

    private fun removeRedditEmptyCookie() {
        runCatching {
            if (cookieManager.containsRedditDummyCookie()) {
                cookieManager.setCookie(HTTPS_WWW_REDDIT_COM, REDDIT_SESSION_EXPIRED_)
            }
        }
    }

    private fun CookieManagerWrapper.containsRedditDummyCookie(): Boolean {
        val redditCookies = this.getCookie(HTTPS_WWW_REDDIT_COM) ?: ""
        val redditSessionCookies = redditCookies.split(";").filter { it.contains("reddit_session") }
        return redditSessionCookies.firstOrNull()?.split("=")?.lastOrNull()?.isEmpty() == true
    }
}

// This class is basically a convenience wrapper for easier testing
interface CookieManagerWrapper {
    /**
     * @return the cookie stored for the given [url] if any, null otherwise
     */
    fun getCookie(url: String): String?

    fun setCookie(url: String, cookieString: String)
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

@Module
@ContributesTo(AppScope::class)
object CookieManagerWrapperModule {
    @Provides
    @InternalApi
    fun providesCookieManagerWrapper(): CookieManagerWrapper {
        return CookieManagerWrapperImpl()
    }
}
private class CookieManagerWrapperImpl constructor() : CookieManagerWrapper {

    private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }
    override fun getCookie(url: String): String? {
        return cookieManager.getCookie(url)
    }

    override fun setCookie(domain: String, cookie: String) {
        cookieManager.setCookie(domain, cookie)
        cookieManager.flush()
    }
}
