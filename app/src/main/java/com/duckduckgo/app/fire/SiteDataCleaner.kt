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

package com.duckduckgo.app.fire

import android.annotation.SuppressLint
import android.webkit.WebStorage
import androidx.webkit.WebStorageCompat
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

fun interface SiteDataCleaner {
    suspend fun deleteSiteData(webStorage: WebStorage, domain: String)
}

@ContributesBinding(AppScope::class)
@SuppressLint("RequiresFeature")
class RealSiteDataCleaner @Inject constructor() : SiteDataCleaner {
    override suspend fun deleteSiteData(webStorage: WebStorage, domain: String) {
        withTimeoutOrNull(SITE_DELETION_TIMEOUT_MS.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                WebStorageCompat.deleteBrowsingDataForSite(webStorage, domain) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    private companion object {
        private const val SITE_DELETION_TIMEOUT_MS = 5_000L
    }
}
