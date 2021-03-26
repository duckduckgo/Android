/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.cookies

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.app.browser.cookies.db.AuthCookieAllowedDomainEntity
import com.duckduckgo.app.browser.cookies.db.AuthCookiesAllowedDomainsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

interface ThirdPartyCookieManager {
    suspend fun processUriForThirdPartyCookies(webView: WebView, uri: Uri)
    suspend fun clearAllData()
}

class AppThirdPartyCookieManager(
    private val cookieManager: CookieManager,
    private val authCookiesAllowedDomainsRepository: AuthCookiesAllowedDomainsRepository
) : ThirdPartyCookieManager {

    override suspend fun processUriForThirdPartyCookies(webView: WebView, uri: Uri) {
        if (uri.host == GOOGLE_ACCOUNTS_HOST) {
            addHostToList(uri)
        } else {
            processThirdPartyCookiesSetting(webView, uri)
        }
    }

    override suspend fun clearAllData() {
        authCookiesAllowedDomainsRepository.deleteAll(hostsThatAlwaysRequireThirdPartyCookies)
    }

    private suspend fun processThirdPartyCookiesSetting(webView: WebView, uri: Uri) {
        val host = uri.host ?: return
        val domain = authCookiesAllowedDomainsRepository.getDomain(host)
        withContext(Dispatchers.Main) {
            if (domain != null && hasUserIdCookie()) {
                Timber.d("Cookies enabled for $uri")
                cookieManager.setAcceptThirdPartyCookies(webView, true)
            } else {
                Timber.d("Cookies disabled for $uri")
                cookieManager.setAcceptThirdPartyCookies(webView, false)
            }
            domain?.let { deleteHost(it) }
        }
    }

    private suspend fun deleteHost(authCookieAllowedDomainEntity: AuthCookieAllowedDomainEntity) {
        if (hostsThatAlwaysRequireThirdPartyCookies.contains(authCookieAllowedDomainEntity.domain)) return
        authCookiesAllowedDomainsRepository.removeDomain(authCookieAllowedDomainEntity)
    }

    private suspend fun addHostToList(uri: Uri) {
        val ssDomain = uri.getQueryParameter(SS_DOMAIN)
        val accessType = uri.getQueryParameter(RESPONSE_TYPE)
        ssDomain?.let {
            if (accessType?.contains(CODE) == false) {
                ssDomain.toUri().host?.let {
                    authCookiesAllowedDomainsRepository.addDomain(it)
                }
            }
        }
    }

    private fun hasUserIdCookie(): Boolean {
        return cookieManager.getCookie(GOOGLE_ACCOUNTS_URL)?.split(";")?.firstOrNull {
            it.contains(USER_ID_COOKIE)
        } != null
    }

    companion object {
        private const val SS_DOMAIN = "ss_domain"
        private const val RESPONSE_TYPE = "response_type"
        private const val CODE = "code"
        const val USER_ID_COOKIE = "user_id"
        const val GOOGLE_ACCOUNTS_URL = "https://accounts.google.com"
        const val GOOGLE_ACCOUNTS_HOST = "accounts.google.com"
        val hostsThatAlwaysRequireThirdPartyCookies = listOf("home.nest.com")
    }
}
