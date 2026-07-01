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
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        // Decide per jar: each mode's session is read and mutated in isolation, so a logged-out
        // profile can't clobber a real reddit_session living in another profile (e.g. Fire).
        val vpnEnabled = networkProtectionState.isEnabled()
        forEachResolvedProfile { jar ->
            val redditSessionCookies = jar.redditSessionCookies()
            if (vpnEnabled && redditSessionCookies.isEmpty()) {
                // if the VPN is enabled and there's no reddit_session cookie we just add a fake one
                // when the user logs into reddit, the fake reddit_session cookie is replaced automatically by the correct one
                // when the user logs out, the reddit_session cookie is cleared
                jar.setCookie(HTTPS_WWW_REDDIT_COM, REDDIT_SESSION_)
            } else {
                jar.expireDummyIfPresent()
            }
        }
    }

    private suspend fun removeRedditEmptyCookie() {
        forEachResolvedProfile { jar ->
            jar.expireDummyIfPresent()
        }
    }

    private suspend fun forEachResolvedProfile(action: (CookieJar) -> Unit) {
        runCatching {
            withContext(dispatcherProvider.io()) {
                cookieManager.resolvedProfiles().forEach(action)
            }
        }.onFailure {
            logcat { "Reddit workaround error: ${it.asLog()}" }
        }
    }

    private fun CookieJar.expireDummyIfPresent() {
        if (containsRedditDummyCookie()) {
            setCookie(HTTPS_WWW_REDDIT_COM, REDDIT_SESSION_EXPIRED_)
        }
    }

    private fun CookieJar.redditSessionCookies(): List<String> {
        val redditCookies = getCookie(HTTPS_WWW_REDDIT_COM) ?: ""
        return redditCookies.split(";").filter { it.contains("reddit_session") }
    }

    private fun CookieJar.containsRedditDummyCookie(): Boolean {
        return redditSessionCookies().firstOrNull()?.split("=")?.lastOrNull()?.isEmpty() == true
    }
}

// This class is basically a convenience wrapper for easier testing
interface CookieManagerWrapper {
    /**
     * Returns a handle for each already-resolved browser-mode cookie jar (the default profile plus
     * any non-default profiles). Jars that can't be resolved on the current thread are omitted, and
     * browser modes that share a jar collapse to a single entry.
     */
    fun resolvedProfiles(): List<CookieJar>
}

// A single browser-mode cookie jar.
interface CookieJar {
    /**
     * @return the cookie stored for the given [url] in this jar if any, null otherwise
     */
    fun getCookie(url: String): String?

    /**
     * Sets the given [cookieString] for the given [url] in this jar, flushing it.
     */
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
    fun providesCookieManagerWrapper(cookieManagerProvider: CookieManagerProvider): CookieManagerWrapper {
        return CookieManagerWrapperImpl(cookieManagerProvider)
    }
}
private class CookieManagerWrapperImpl constructor(
    private val cookieManagerProvider: CookieManagerProvider,
) : CookieManagerWrapper {

    override fun resolvedProfiles(): List<CookieJar> {
        return BrowserMode.entries
            .mapNotNull { cookieManagerProvider.forMode(it) }
            .distinct()
            .map { RealCookieJar(it) }
    }
}

private class RealCookieJar(private val cookieManager: CookieManager) : CookieJar {
    override fun getCookie(url: String): String? = cookieManager.getCookie(url)

    override fun setCookie(url: String, cookieString: String) {
        cookieManager.setCookie(url, cookieString)
        cookieManager.flush()
    }
}
