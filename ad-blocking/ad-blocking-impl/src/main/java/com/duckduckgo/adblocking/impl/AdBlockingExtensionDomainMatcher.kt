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

package com.duckduckgo.adblocking.impl

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.browser.Domain
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Single source of truth for the domains the ad-blocking extension operates on. A subdomain
 * match means `youtube.com` also covers `m.youtube.com`, `www.youtube.com`, etc.
 */

interface AdBlockingExtensionDomainMatcher {
    fun matches(url: String?): Boolean

    fun matches(uri: Uri): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAdBlockingExtensionDomainMatcher @Inject constructor() : AdBlockingExtensionDomainMatcher {

    override fun matches(url: String?): Boolean {
        val uri = url?.toUri() ?: return false
        return matches(uri)
    }

    override fun matches(uri: Uri): Boolean = DOMAINS.any { UriString.sameOrSubdomain(uri, it) }

    private companion object {
        val DOMAINS = listOf(
            Domain("youtube.com"),
            Domain("youtube-nocookie.com"),
        )
    }
}
