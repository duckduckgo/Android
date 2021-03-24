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
import com.duckduckgo.app.browser.cookies.db.AllowedDomainEntity
import com.duckduckgo.app.browser.cookies.db.AllowedDomainsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

interface ThirdPartyCookieManager {
    suspend fun processUriForThirdPartyCookies(webView: WebView, uri: Uri)
    suspend fun clearAllData()
}

class AppThirdPartyCookieManager(
    private val cookieManager: CookieManager,
    private val allowedDomainsRepository: AllowedDomainsRepository
) : ThirdPartyCookieManager {

    override suspend fun processUriForThirdPartyCookies(webView: WebView, uri: Uri) {
        if (uri.host == GOOGLE_ACCOUNTS_HOST) {
            addHostToList(uri)
        } else {
            enableThirdPartyCookies(webView, uri)
        }
    }

    override suspend fun clearAllData() {
        allowedDomainsRepository.deleteAll(hostsThatAlwaysRequireThirdPartyCookies)
    }

    private suspend fun enableThirdPartyCookies(webView: WebView, uri: Uri) {
        val host = uri.host ?: return
        val allowedDomain = allowedDomainsRepository.getDomain(host)
        withContext(Dispatchers.Main) {
            if (allowedDomain != null && hasUserIdCookie()) {
                Timber.d("Cookies enabled for $uri")
                cookieManager.setAcceptThirdPartyCookies(webView, true)
                deleteHost(allowedDomain)
            } else {
                Timber.d("Cookies disabled for $uri")
                allowedDomain?.let { deleteHost(it) }
                cookieManager.setAcceptThirdPartyCookies(webView, false)
            }
        }
    }

    private suspend fun deleteHost(allowedDomainEntity: AllowedDomainEntity) {
        if (hostsThatAlwaysRequireThirdPartyCookies.contains(allowedDomainEntity.domain)) return
        allowedDomainsRepository.removeDomain(allowedDomainEntity)
    }

    private suspend fun addHostToList(uri: Uri) {
        val ssDomain = uri.getQueryParameter(SS_DOMAIN)
        val accessType = uri.getQueryParameter(RESPONSE_TYPE)
        ssDomain?.let {
            if (accessType?.contains(CODE) == false) {
                ssDomain.toUri().host?.let {
                    allowedDomainsRepository.addDomain(it)
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
