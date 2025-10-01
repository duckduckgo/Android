/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.global.model

import android.util.LruCache
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.duckduckgo.app.brokensite.RealBrokenSiteContext
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.certificates.BypassedSSLCertificatesRepository
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SiteFactoryImpl @Inject constructor(
    private val entityLookup: EntityLookup,
    private val contentBlocking: ContentBlocking,
    private val userAllowListRepository: UserAllowListRepository,
    private val bypassedSSLCertificatesRepository: BypassedSSLCertificatesRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val duckPlayer: DuckPlayer,
) : SiteFactory {

    private val siteCache = LruCache<String, Site>(1)

    /**
     * Builds a Site with minimal details; this is quick to build but won't contain the full details needed for all functionality
     *
     * @see [loadFullSiteDetails] to ensure full privacy details are loaded
     */
    @AnyThread
    override fun buildSite(
        url: String,
        tabId: String,
        title: String?,
        httpUpgraded: Boolean,
        externalLaunch: Boolean,
    ): Site {
        val cacheKey = "$tabId|$url"
        val cachedSite = siteCache.get(cacheKey)
        return if (cachedSite == null) {
            SiteMonitor(
                url,
                title,
                httpUpgraded,
                externalLaunch,
                userAllowListRepository,
                contentBlocking,
                bypassedSSLCertificatesRepository,
                appCoroutineScope,
                dispatcherProvider,
                RealBrokenSiteContext(duckDuckGoUrlDetector),
                duckPlayer,
            ).also {
                siteCache.put(cacheKey, it)
            }
        } else {
            cachedSite.upgradedHttps = httpUpgraded
            cachedSite.title = title

            cachedSite
        }
    }

    /**
     * Updates the given Site with the full details
     *
     * This can be expensive to execute.
     */
    @WorkerThread
    override fun loadFullSiteDetails(site: Site) {
        val memberNetwork = entityLookup.entityForUrl(site.url)
        val siteDetails = SitePrivacyData(site.url, memberNetwork, memberNetwork?.prevalence ?: 0.0)
        site.updatePrivacyData(siteDetails)
    }
}
