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

package com.duckduckgo.remote.messaging.internal.feature

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.internal.setting.RmfConfigMode
import com.duckduckgo.remote.messaging.internal.setting.RmfConfigSourceStore
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import okhttp3.HttpUrl
import javax.inject.Inject

interface RmfConfigUrlResolver {
    /** @return the URL the RMF request should use, or null to leave the request untouched. */
    fun resolve(requestUrl: HttpUrl): String?
}

@ContributesBinding(AppScope::class)
class RealRmfConfigUrlResolver @Inject constructor(
    private val rmfConfigSourceStore: RmfConfigSourceStore,
) : RmfConfigUrlResolver {

    override fun resolve(requestUrl: HttpUrl): String? = resolveRmfConfigUrl(
        mode = rmfConfigSourceStore.mode,
        prNumber = rmfConfigSourceStore.prNumber,
        customUrl = rmfConfigSourceStore.customUrl,
        requestUrl = requestUrl,
    )
}

/** RMF production config URL as declared by RemoteMessagingService; the URL every fetch starts from. */
const val RMF_PROD_CONFIG_URL = "https://staticcdn.duckduckgo.com/remotemessaging/config/v1/android-config.json"

private const val RMF_BASE = "https://staticcdn.duckduckgo.com/remotemessaging/"
private const val RMF_STAGING = "https://staticcdn.duckduckgo.com/remotemessaging/config/staging/"

/**
 * Pure resolution shared by the interceptor and the settings-screen "effective URL" preview.
 * @return the URL to use, or null to leave the request untouched (production / misconfigured).
 */
fun resolveRmfConfigUrl(
    mode: RmfConfigMode,
    prNumber: String,
    customUrl: String,
    requestUrl: HttpUrl,
): String? {
    // Only touch production RMF requests, so unrelated traffic on the shared client is never rewritten.
    if (!requestUrl.isProductionRmf()) return null

    val lastSegment = requestUrl.encodedPathSegments.last()
    return when (mode) {
        RmfConfigMode.PRODUCTION -> null
        RmfConfigMode.STAGING -> RMF_STAGING + lastSegment
        RmfConfigMode.PR_NUMBER -> {
            val pr = prNumber.trim()
            if (pr.isEmpty()) {
                logcat(tag = "RMF") { "PR mode with blank PR number, falling back to staging default" }
                RMF_STAGING + lastSegment
            } else {
                RMF_STAGING + pr + "/" + lastSegment
            }
        }
        RmfConfigMode.CUSTOM_URL -> {
            val custom = customUrl.trim()
            if (custom.startsWith("http")) {
                custom
            } else {
                logcat(tag = "RMF") { "custom URL mode with blank/invalid URL, leaving request untouched" }
                null
            }
        }
    }
}

private fun HttpUrl.isProductionRmf(): Boolean = toString().let {
    it.contains(RMF_BASE) && !it.contains(RMF_STAGING)
}
