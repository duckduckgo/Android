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
import android.webkit.WebStorage
import androidx.webkit.ProfileStore
import com.duckduckgo.app.browser.api.WebStorageProvider
import com.duckduckgo.app.browser.api.WebViewProfileManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DefaultWebStorageProvider @Inject constructor(
    private val webViewProfileManager: Lazy<WebViewProfileManager>,
    private val dispatchers: DispatcherProvider,
) : WebStorageProvider {

    @SuppressLint("RequiresFeature")
    override suspend fun get(): WebStorage {
        val profileManager = webViewProfileManager.get()

        // If profile switching is not available, return default
        if (!profileManager.isProfileSwitchingAvailable()) {
            return WebStorage.getInstance()
        }

        val profileName = profileManager.getCurrentProfileName()

        // If profile name is empty, return default
        if (profileName.isEmpty()) {
            return WebStorage.getInstance()
        }

        return withContext(dispatchers.main()) {
            try {
                val profileStore = ProfileStore.getInstance()
                profileStore.getProfile(profileName)?.webStorage ?: WebStorage.getInstance()
            } catch (e: Exception) {
                logcat(WARN) { "Failed to get profile WebStorage, falling back to default: ${e.asLog()}" }
                WebStorage.getInstance()
            }
        }
    }
}
