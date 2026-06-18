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

package com.duckduckgo.site.preferences.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.site.preferences.api.DesktopModeSettings
import com.duckduckgo.site.preferences.api.SitePreferencesDataClearer
import com.duckduckgo.site.preferences.impl.store.SitePreferencesDao
import com.duckduckgo.site.preferences.impl.store.SitePreferencesEntity
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject

@ContributesBinding(AppScope::class, boundType = DesktopModeSettings::class)
@ContributesBinding(AppScope::class, boundType = SitePreferencesDataClearer::class)
@ContributesMultibinding(AppScope::class, boundType = MainProcessLifecycleObserver::class)
@SingleInstanceIn(AppScope::class)
class RealSitePreferencesRepository @Inject constructor(
    private val sitePreferencesDao: SitePreferencesDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @IsMainProcess private val isMainProcess: Boolean,
) : DesktopModeSettings, SitePreferencesDataClearer, MainProcessLifecycleObserver {

    private val desktopModeDomains = CopyOnWriteArraySet<String>()

    @Volatile private var cachePrimed = false

    override fun onCreate(owner: LifecycleOwner) {
        if (!isMainProcess) return
        appCoroutineScope.launch(dispatcherProvider.io()) {
            "example.com".toTldPlusOne()
            sitePreferencesDao.desktopModeDomainsFlow().collect { domains ->
                desktopModeDomains.clear()
                desktopModeDomains.addAll(domains)
                cachePrimed = true
            }
        }
    }

    override suspend fun isDesktopModeRemembered(domain: String): Boolean {
        if (cachePrimed) return desktopModeDomains.contains(domain)
        if (desktopModeDomains.contains(domain)) return true
        return withContext(dispatcherProvider.io()) { sitePreferencesDao.isDesktopModeEnabled(domain) }
    }

    override fun isDesktopModeRememberedInCache(domain: String): Boolean = desktopModeDomains.contains(domain)

    override fun rememberDesktopMode(domain: String) {
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
        withContext(dispatcherProvider.io()) { sitePreferencesDao.delete(domains) }
    }

    override suspend fun clearAllButFireproofed(fireproofedDomains: Set<String>) {
        desktopModeDomains.retainAll(fireproofedDomains)
        withContext(dispatcherProvider.io()) { sitePreferencesDao.deleteAllExcept(fireproofedDomains) }
    }
}
