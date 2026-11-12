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

package com.duckduckgo.privacy.config.impl.observers

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.PirProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<PirProcessLifecycleObserver>(),
)
class PirProcessPrivacyConfigLoader @Inject constructor(
    private val trackerAllowlistRepository: TrackerAllowlistRepository,
    private val contentBlockingRepository: ContentBlockingRepository,
    private val unprotectedTemporaryRepository: UnprotectedTemporaryRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : PirProcessLifecycleObserver {

    override fun onPirProcessCreated() {
        // Repositories only self-load in the main process, but PIR's tracker blocking evaluates requests in :pir,
        // where an empty allowlist would block site-breakage exceptions such as captcha providers.
        appCoroutineScope.launch(dispatcherProvider.io()) {
            listOf(
                trackerAllowlistRepository::loadToMemory,
                contentBlockingRepository::loadToMemory,
                unprotectedTemporaryRepository::loadToMemory,
            ).forEach { load ->
                // The app scope has no exception handler, so a failed load would crash :pir; an empty list only loses its exceptions.
                runCatching { load() }.onFailure { logcat(ERROR) { "Failed to load privacy config exceptions in :pir: ${it.asLog()}" } }
            }
        }
    }
}
