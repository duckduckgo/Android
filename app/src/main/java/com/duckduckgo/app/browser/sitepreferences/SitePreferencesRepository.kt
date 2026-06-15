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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface SitePreferencesRepository {

    /**
     * Synchronous, in-memory check — safe to call on the WebView network thread.
     * @param domain a registrable domain (eTLD+1).
     */
    fun isDesktopModeRemembered(domain: String): Boolean

    /** Remember that [domain] (eTLD+1) should always open in desktop mode. */
    fun rememberDesktopMode(domain: String)

    /** Forget the desktop-mode preference for [domain] (eTLD+1). */
    fun forgetDesktopMode(domain: String)

    /** Forget the desktop-mode preference for all of [domains] (eTLD+1) — used by single-tab burn. */
    suspend fun forgetDesktopMode(domains: Set<String>)

    /** Forget every preference except those for [fireproofedDomains] (eTLD+1) — used by the fire button. */
    suspend fun clearAllButFireproofed(fireproofedDomains: Set<String>)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSitePreferencesRepository @Inject constructor(
    private val sitePreferencesDao: SitePreferencesDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @IsMainProcess isMainProcess: Boolean,
) : SitePreferencesRepository {

    private val desktopModeDomains = CopyOnWriteArraySet<String>()

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                // Warm OkHttp's PublicSuffixDatabase: the first toTldPlusOne() call reads the suffix
                // list from disk on the calling thread, which would otherwise block the network thread.
                "example.com".toTldPlusOne()
                sitePreferencesDao.desktopModeDomainsFlow().collect { domains ->
                    desktopModeDomains.clear()
                    desktopModeDomains.addAll(domains)
                }
            }
        }
    }

    override fun isDesktopModeRemembered(domain: String): Boolean = desktopModeDomains.contains(domain)

    override fun rememberDesktopMode(domain: String) {
        // Optimistic update so the immediate post-toggle reload's interceptor read sees the new value.
        // Accepted race: a Flow emission computed before the DB write below lands can momentarily revert
        // the cache; the insert's own emission converges it. No locking for this quick win.
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
