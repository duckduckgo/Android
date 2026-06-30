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

package com.duckduckgo.subscriptions.impl.serp_promo

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.cookies.api.setCookieForAllModes
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject
import javax.inject.Qualifier

interface SerpPromo {
    suspend fun injectCookie(cookieValue: String?)
}

private const val HTTPS_WWW_SUBSCRIPTION_DDG_COM = ".subscriptions.duckduckgo.com"
private const val SERP_PPRO_PROMO_COOKIE_NAME = "privacy_pro_access_token"

@ContributesBinding(
    scope = AppScope::class,
    boundType = SerpPromo::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class RealSerpPromo @Inject constructor(
    @InternalApi private val cookieManager: CookieManagerWrapper,
    private val dispatcherProvider: DispatcherProvider,
    private val subscriptionsFeature: Lazy<SubscriptionsFeature>,
    private val subscriptions: Lazy<Subscriptions>, // break dep cycle
) : SerpPromo, MainProcessLifecycleObserver {

    override suspend fun injectCookie(cookieValue: String?) {
        if (!subscriptionsFeature.get().serpPromoCookie().isEnabled()) return

        withContext(dispatcherProvider.io()) {
            synchronized(cookieManager) {
                kotlin.runCatching {
                    val cookieString = "$SERP_PPRO_PROMO_COOKIE_NAME=${cookieValue.orEmpty()};HttpOnly;Path=/;"
                    cookieManager.setCookieOnAllProfiles(HTTPS_WWW_SUBSCRIPTION_DDG_COM, cookieString)
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(dispatcherProvider.io()) {
            if (subscriptionsFeature.get().serpPromoCookie().isEnabled()) {
                kotlin.runCatching {
                    val accessToken = subscriptions.get().getAccessToken() ?: ""
                    injectCookie(accessToken)
                }
            }
        }
    }
}

// This class is basically a convenience wrapper for easier testing
interface CookieManagerWrapper {
    /**
     * @return the cookie stored for the given [url] if any, null otherwise
     */
    fun getCookie(url: String): String?

    /**
     * Sets the given [cookieString] for the given [url] on every browser mode's cookie jar (the
     * default profile plus any non-default profiles).
     */
    fun setCookieOnAllProfiles(url: String, cookieString: String)
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

    override fun getCookie(url: String): String? {
        return cookieManagerProvider.forMode(BrowserMode.REGULAR)?.getCookie(url)
    }

    override fun setCookieOnAllProfiles(url: String, cookieString: String) {
        logcat { "Setting cookie $cookieString for domain $url" }
        cookieManagerProvider.setCookieForAllModes(url, cookieString)
    }
}
