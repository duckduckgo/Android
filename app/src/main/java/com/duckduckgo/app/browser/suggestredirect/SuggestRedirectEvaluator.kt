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

package com.duckduckgo.app.browser.suggestredirect

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RedirectSuggestion(
    val domain: String,
    val url: String,
)

interface SuggestRedirectEvaluator {
    /**
     * Decides whether the error page for a failed [url] due to unresolved host should suggest
     * redirecting to the "www." prefixed variant of its host.
     *
     * @return the suggestion to offer when [url] points at a bare domain (no "www." prefix)
     * whose "www." variant resolves; `null` otherwise.
     */
    suspend fun suggestRedirect(url: String): RedirectSuggestion?
}

@ContributesBinding(AppScope::class)
class RealSuggestRedirectEvaluator @Inject constructor(
    private val hostnameResolver: HostnameResolver,
    private val dispatcherProvider: DispatcherProvider,
) : SuggestRedirectEvaluator {
    override suspend fun suggestRedirect(url: String): RedirectSuggestion? = withContext(dispatcherProvider.io()) {
        val uri = url.toUri()
        val hostname = uri.host ?: return@withContext null

        if (hostname.startsWith(WWW_PREFIX)) {
            return@withContext null
        }

        if (hostname != hostname.toTldPlusOne()) {
            // Non-registrable (eTLD+1) domains are rejected (e.g. localhost, a.b.domain.com)
            return@withContext null
        }

        val suggestedDomain = "${WWW_PREFIX}$hostname"
        if (!hostnameResolver.resolves(suggestedDomain)) {
            return@withContext null
        }

        return@withContext RedirectSuggestion(
            domain = suggestedDomain,
            url = uri.replaceHost(suggestedDomain).toString(),
        )
    }

    private fun Uri.replaceHost(host: String): Uri {
        val authority = if (this.port != -1) "$host:${this.port}" else host
        return this.buildUpon().encodedAuthority(authority).build()
    }

    companion object {
        private const val WWW_PREFIX = "www."
    }
}
