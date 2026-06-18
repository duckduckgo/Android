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

package com.duckduckgo.app.browser.sitepreferences

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject

interface SitePreferencesRepository {

    /**
     * Whether [domain] should open in desktop mode. Correct from the very first navigation: reads the
     * in-memory cache and, only during the brief window before the cache has been primed at startup,
     * falls back to a direct DB read so a remembered site never loads in mobile mode by accident. The
     * DB read is why this suspends — call it from any coroutine (e.g. the request interceptor).
     * @param domain a site key: eTLD+1, or the raw host when there is no registrable domain.
     */
    suspend fun isDesktopModeRemembered(domain: String): Boolean

    /**
     * Synchronous, in-memory-only snapshot for callers that cannot suspend — specifically the page-load
     * path, which runs on the main thread (where Room forbids DB access) and must resolve the mode inline.
     * May be momentarily stale only during the startup priming window; it self-corrects on the next
     * navigation event, and the content load itself is governed by the suspending [isDesktopModeRemembered].
     * @param domain a site key: eTLD+1, or the raw host when there is no registrable domain.
     */
    fun isDesktopModeRememberedInCache(domain: String): Boolean

    /** Remember that [domain] (site key) should always open in desktop mode. */
    fun rememberDesktopMode(domain: String)

    /** Forget the desktop-mode preference for [domain] (site key). */
    fun forgetDesktopMode(domain: String)

    /** Forget the desktop-mode preference for all of [domains] (site keys) — used by single-tab burn. */
    suspend fun forgetDesktopMode(domains: Set<String>)

    /** Forget every preference except those for [fireproofedDomains] (eTLD+1) — used by the fire button. */
    suspend fun clearAllButFireproofed(fireproofedDomains: Set<String>)
}

@ContributesBinding(AppScope::class, boundType = SitePreferencesRepository::class)
@ContributesMultibinding(AppScope::class, boundType = MainProcessLifecycleObserver::class)
@SingleInstanceIn(AppScope::class)
class RealSitePreferencesRepository @Inject constructor(
    private val sitePreferencesDao: SitePreferencesDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @IsMainProcess private val isMainProcess: Boolean,
) : SitePreferencesRepository, MainProcessLifecycleObserver {

    private val desktopModeDomains = CopyOnWriteArraySet<String>()

    // Whether the cache reflects the DB yet. Until the priming collector's first emission lands, an
    // absent domain doesn't mean "not remembered" — it might just be unread. @Volatile for cross-thread
    // visibility: the collector writes it on IO, the interceptor reads it on the WebView network thread.
    @Volatile private var cachePrimed = false

    // Prime the cache at app startup (this observer is constructed eagerly on the main process) rather
    // than lazily on first read, so a remembered site loads in the correct mode from the first navigation.
    override fun onCreate(owner: LifecycleOwner) {
        if (!isMainProcess) return
        appCoroutineScope.launch(dispatcherProvider.io()) {
            // Warm OkHttp's PublicSuffixDatabase: the first toTldPlusOne() call reads the suffix
            // list from disk on the calling thread, which would otherwise block the network thread.
            "example.com".toTldPlusOne()
            sitePreferencesDao.desktopModeDomainsFlow().collect { domains ->
                desktopModeDomains.clear()
                desktopModeDomains.addAll(domains)
                cachePrimed = true
            }
        }
    }

    override suspend fun isDesktopModeRemembered(domain: String): Boolean {
        // Once primed, the cache mirrors the DB — read it directly (no DB hit, even for genuine misses).
        if (cachePrimed) return desktopModeDomains.contains(domain)
        // Not primed yet. An optimistic add by rememberDesktopMode() is still authoritative.
        if (desktopModeDomains.contains(domain)) return true
        // Pre-priming window: read the DB directly so the first load still gets the correct mode. This
        // is authoritative regardless of whether priming completes concurrently, so there's no stale miss.
        return withContext(dispatcherProvider.io()) { sitePreferencesDao.isDesktopModeEnabled(domain) }
    }

    override fun isDesktopModeRememberedInCache(domain: String): Boolean = desktopModeDomains.contains(domain)

    override fun rememberDesktopMode(domain: String) {
        // Optimistic update so the immediate post-toggle reload's interceptor read sees the new value.
        desktopModeDomains.add(domain)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            sitePreferencesDao.insert(SitePreferencesEntity(domain = domain, desktopModeEnabled = true))
        }
    }

    override fun forgetDesktopMode(domain: String) {
        desktopModeDomains.remove(domain)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            sitePreferencesDao.delete(domain)
        }
    }

    override suspend fun forgetDesktopMode(domains: Set<String>) {
        desktopModeDomains.removeAll(domains)
        withContext(dispatcherProvider.io()) {
            sitePreferencesDao.delete(domains)
        }
    }

    override suspend fun clearAllButFireproofed(fireproofedDomains: Set<String>) {
        desktopModeDomains.retainAll(fireproofedDomains)
        withContext(dispatcherProvider.io()) {
            sitePreferencesDao.deleteAllExcept(fireproofedDomains)
        }
    }
}
