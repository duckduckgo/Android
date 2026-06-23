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

import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.common.utils.extensions.toTldPlusOneOrSelf
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

    override suspend fun isDesktopModeRemembered(url: String): Boolean {
        val key = url.siteKeyOrNull() ?: return false
        if (cachePrimed) return desktopModeDomains.contains(key)
        return withContext(dispatcherProvider.io()) { sitePreferencesDao.isDesktopModeEnabled(key) }
    }

    override fun isDesktopModeRememberedInCache(url: String): Boolean =
        url.siteKeyOrNull()?.let { desktopModeDomains.contains(it) } ?: false

    override fun rememberDesktopMode(url: String) {
        val key = url.siteKeyOrNull() ?: return
        desktopModeDomains.add(key)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            sitePreferencesDao.insert(SitePreferencesEntity(domain = key, desktopModeEnabled = true))
        }
    }

    override fun forgetDesktopMode(url: String) {
        val key = url.siteKeyOrNull() ?: return
        desktopModeDomains.remove(key)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            sitePreferencesDao.delete(key)
        }
    }

    override suspend fun clear(domains: Set<String>) {
        val keys = domains.mapTo(mutableSetOf()) { it.toTldPlusOneOrSelf() }
        desktopModeDomains.removeAll(keys)
        withContext(dispatcherProvider.io()) { sitePreferencesDao.delete(keys) }
    }

    override suspend fun clearAllButFireproofed(fireproofedDomains: Set<String>) {
        val keys = fireproofedDomains.mapTo(mutableSetOf()) { it.toTldPlusOneOrSelf() }
        desktopModeDomains.retainAll(keys)
        withContext(dispatcherProvider.io()) { sitePreferencesDao.deleteAllExcept(keys) }
    }

    // Settings callers pass a raw page URL; derive the site key (eTLD+1, host fallback) from its host.
    private fun String.siteKeyOrNull(): String? = toUri().host?.toTldPlusOneOrSelf()
}
