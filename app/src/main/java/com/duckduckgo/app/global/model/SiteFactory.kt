/*
 * Copyright (c) 2018 DuckDuckGo
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
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.di.scopes.AppObjectGraph
import javax.inject.Inject
import dagger.SingleInstanceIn

@SingleInstanceIn(AppObjectGraph::class)
class SiteFactory @Inject constructor(
    private val privacyPractices: PrivacyPractices,
    private val entityLookup: EntityLookup
) {

    /**
     * Builds a Site with minimal details; this is quick to build but won't contain the full details needed for all functionality
     *
     * @see [loadFullSiteDetails] to ensure full privacy details are loaded
     */
    @AnyThread
    fun buildSite(url: String, title: String? = null, httpUpgraded: Boolean = false): Site {
        return SiteMonitor(url, title, httpUpgraded)
    }

    /**
     * Updates the given Site with the full details
     *
     * This can be expensive to execute.
     */
    @WorkerThread
    fun loadFullSiteDetails(site: Site) {
        val practices = privacyPractices.privacyPracticesFor(site.url)
        val memberNetwork = entityLookup.entityForUrl(site.url)
        val siteDetails = SitePrivacyData(site.url, practices, memberNetwork, memberNetwork?.prevalence ?: 0.0)
        site.updatePrivacyData(siteDetails)
    }

    data class SitePrivacyData(
        val url: String,
        val practices: PrivacyPractices.Practices,
        val entity: Entity?,
        val prevalence: Double?
    )
}
