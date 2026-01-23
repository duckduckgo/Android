/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.webview.profile

import android.annotation.SuppressLint
import android.webkit.CookieManager
import androidx.annotation.MainThread
import androidx.webkit.ProfileStore
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

/**
 * Handles migration of profile data (cookies and IndexedDB) when switching WebView profiles.
 */
interface WebViewProfileMigrationManager {
    /**
     * Migrates profile data from old profile to new profile.
     * This includes:
     * - Fireproofed cookies
     * - IndexedDB data for Duck.ai domains (if chats should be preserved)
     *
     * @param oldProfileName The name of the profile to migrate from
     * @param newProfileName The name of the profile to migrate to
     */
    @MainThread
    suspend fun migrateProfileData(
        oldProfileName: String,
        newProfileName: String,
    )
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealWebViewProfileMigrationManager @Inject constructor(
    private val fireproofRepository: FireproofRepository,
    private val dispatchers: DispatcherProvider,
) : WebViewProfileMigrationManager {

    @MainThread
    override suspend fun migrateProfileData(
        oldProfileName: String,
        newProfileName: String,
    ) {
        // Always migrate fireproofed cookies
        migrateFireproofedCookies(oldProfileName, newProfileName)
    }

    @SuppressLint("RequiresFeature")
    private suspend fun migrateFireproofedCookies(
        oldProfileName: String,
        newProfileName: String,
    ) {
        try {
            // Fetch fireproof domains on IO thread (database access)
            val fireproofDomains = withContext(dispatchers.io()) {
                fireproofRepository.fireproofWebsites()
            }
            logcat { "Migrating cookies for ${fireproofDomains.size} fireproofed domains + ${DDG_DOMAINS.size} DDG domains" }

            // ProfileStore operations must be on main thread
            withContext(dispatchers.main()) {
                val profileStore = ProfileStore.getInstance()

                // Get old profile's cookie manager (or default if empty profile name)
                val oldCookieManager = if (oldProfileName.isEmpty()) {
                    CookieManager.getInstance()
                } else {
                    profileStore.getProfile(oldProfileName)?.cookieManager ?: CookieManager.getInstance()
                }

                // Get new profile's cookie manager
                val newProfile = profileStore.getOrCreateProfile(newProfileName)
                val newCookieManager = newProfile.cookieManager

                // Migrate fireproofed domain cookies
                val domainsToMigrate = fireproofDomains.flatMap { domain ->
                    // Include both with and without https:// prefix for better cookie matching
                    listOf(domain, "https://$domain")
                } + DDG_DOMAINS

                domainsToMigrate.forEach { domain ->
                    migrateCookiesForDomain(oldCookieManager, newCookieManager, domain)
                }

                // Flush to persist cookies
                newCookieManager.flush()

                logcat { "Cookie migration completed from '$oldProfileName' to '$newProfileName'" }
            }
        } catch (e: Exception) {
            logcat(ERROR) { "Failed to migrate cookies: ${e.asLog()}" }
        }
    }

    private fun migrateCookiesForDomain(
        oldCookieManager: CookieManager,
        newCookieManager: CookieManager,
        domain: String,
    ) {
        try {
            val cookies = oldCookieManager.getCookie(domain)
            if (cookies.isNullOrEmpty()) return

            // Cookies are returned as a single string separated by "; "
            // They need to be set one by one
            cookies.split(";").forEach { cookie ->
                val trimmedCookie = cookie.trim()
                if (trimmedCookie.isNotEmpty()) {
                    newCookieManager.setCookie(domain, trimmedCookie)
                }
            }
            logcat { "Migrated cookies for domain: $domain" }
        } catch (e: Exception) {
            logcat(ERROR) { "Failed to migrate cookies for $domain: ${e.asLog()}" }
        }
    }

    private companion object {
        // DuckDuckGo domains that should always have cookies preserved
        val DDG_DOMAINS = listOf(
            AppUrl.Url.COOKIES,
            AppUrl.Url.SURVEY_COOKIES,
            AppUrl.Url.DUCK_AI,
        )
    }
}
