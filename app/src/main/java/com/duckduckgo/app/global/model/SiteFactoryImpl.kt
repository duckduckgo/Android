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

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SiteFactoryImpl @Inject constructor(
    private val entityLookup: EntityLookup,
    private val userAllowListDao: UserAllowListDao,
    private val contentBlocking: ContentBlocking,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SiteFactory {

    /**
     * Builds a Site with minimal details; this is quick to build but won't contain the full details needed for all functionality
     *
     * @see [loadFullSiteDetails] to ensure full privacy details are loaded
     */
    @AnyThread
    override fun buildSite(
        url: String,
        title: String?,
        httpUpgraded: Boolean,
    ): Site {
        return SiteMonitor(url, title, httpUpgraded, userAllowListDao, contentBlocking, appCoroutineScope, dispatcherProvider)
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
