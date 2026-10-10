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

package com.duckduckgo.app.tabs.model

import androidx.core.text.HtmlCompat
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface PageTitleFetcher {
    suspend fun fetchTitle(url: String): String?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPageTitleFetcher @Inject constructor(
    private val userAgentProvider: UserAgentProvider,
    private val gpc: Gpc,
    private val dispatchers: DispatcherProvider,
) : PageTitleFetcher {
    private val client by lazy {
        OkHttpClient.Builder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }

    override suspend fun fetchTitle(url: String): String? = withContext(dispatchers.io()) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgentProvider.userAgent(url))
                .apply { gpc.getHeaders(url).forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: return@use null
                // SVG images carry their own <title>, so only HTML pages are parsed.
                if (!response.isSuccessful || body.contentType()?.subtype?.contains("html") != true) return@use null
                body.source().request(MAX_BYTES)
                parseTitle(body.source().buffer.readUtf8())
            }
        }.getOrNull()
    }

    private fun parseTitle(html: String): String? {
        val raw = TITLE_REGEX.find(html)?.groupValues?.get(1) ?: return null
        return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val MAX_BYTES = 32 * 1024L
        val TITLE_REGEX = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }
}
