/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.cookies.impl

import android.annotation.SuppressLint
import android.os.Looper
import android.webkit.CookieManager
import androidx.webkit.ProfileStore
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.browsermode.api.profileName
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@SuppressLint("RequiresFeature")
class DefaultCookieManagerProvider @Inject constructor(
    private val fireModeAvailability: Lazy<FireModeAvailability>,
) : CookieManagerProvider {

    @Volatile
    private var defaultInstance: CookieManager? = null

    @Volatile
    private var fireInstance: CookieManager? = null

    override fun forMode(mode: BrowserMode): CookieManager? = when (mode) {
        BrowserMode.REGULAR -> defaultManager()
        BrowserMode.FIRE -> if (fireModeAvailability.get().isAvailable()) fireManager() else defaultManager()
    }

    private fun fireManager(): CookieManager? {
        fireInstance?.let { return it }
        // ProfileStore is @UiThread: off the main thread we can't resolve the Fire profile
        if (Looper.myLooper() != Looper.getMainLooper()) {
            logcat(LogPriority.WARN) { "Fire CookieManager requested off the main thread before warm-up; returning null" }
            return null
        }

        return runCatching {
            ProfileStore.getInstance().getOrCreateProfile(BrowserMode.FIRE.profileName).cookieManager
        }.getOrNull()?.also { fireInstance = it }
    }

    private fun defaultManager(): CookieManager? =
        runCatching { defaultInstance ?: CookieManager.getInstance().also { defaultInstance = it } }.getOrNull()
}
