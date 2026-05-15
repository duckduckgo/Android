/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.browsermode.impl.profile

import android.annotation.SuppressLint
import androidx.webkit.Profile
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface WebViewProfileMigrationManager {
    suspend fun migrate(old: Profile, new: Profile)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealWebViewProfileMigrationManager @Inject constructor(
    private val fireproofRepository: FireproofRepository,
    private val dispatchers: DispatcherProvider,
) : WebViewProfileMigrationManager {

    @SuppressLint("RequiresFeature")
    override suspend fun migrate(old: Profile, new: Profile) {
        val domains = (fireproofRepository.fireproofWebsites() + DDG_DOMAINS).distinct()
        withContext(dispatchers.main()) {
            val oldCookies = old.cookieManager
            val newCookies = new.cookieManager
            domains.forEach { domain ->
                val url = if (domain.startsWith("http")) domain else "https://$domain"
                oldCookies.getCookie(url)?.split(";")?.forEach { rawCookie ->
                    val cookie = rawCookie.trim()
                    if (cookie.isNotEmpty()) newCookies.setCookie(url, cookie)
                }
            }
        }
    }

    private companion object {
        val DDG_DOMAINS = listOf(
            AppUrl.Url.COOKIES,
            AppUrl.Url.SURVEY_COOKIES,
            "https://duck.ai",
        )
    }
}
