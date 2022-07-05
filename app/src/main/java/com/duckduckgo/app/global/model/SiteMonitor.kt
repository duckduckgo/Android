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

import android.net.Uri
import android.net.http.SslCertificate
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.global.model.Site.SiteGrades
import com.duckduckgo.app.global.model.SiteFactory.SitePrivacyData
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyGrade.B
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.privacy.dashboard.api.PrivacyShield
import com.duckduckgo.privacy.dashboard.api.PrivacyShield.PROTECTED
import com.duckduckgo.privacy.dashboard.api.PrivacyShield.UNKNOWN
import com.duckduckgo.privacy.dashboard.api.PrivacyShield.UNPROTECTED
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.privacy.config.api.ContentBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

class SiteMonitor(
    url: String,
    override var title: String?,
    override var upgradedHttps: Boolean = false,
    private val userWhitelistDao: UserWhitelistDao,
    private val contentBlocking: ContentBlocking,
    private val appCoroutineScope: CoroutineScope
) : Site {

    override var url: String = url
        set(value) {
            field = value
            _uri = field.toUri()
        }

    /**
     * A read-only uri version of the url. To update, update the url
     */
    override val uri: Uri? get() = _uri

    /**
     * Backing field to ensure uri cannot be publicly written
     */
    private var _uri: Uri? = url.toUri()

    override val https: HttpsStatus
        get() = httpsStatus()

    override var hasHttpResources = false

    override var privacyPractices: PrivacyPractices.Practices = PrivacyPractices.UNKNOWN

    override var entity: Entity? = null

    override var certificate: SslCertificate? = null

    override val trackingEvents = CopyOnWriteArrayList<TrackingEvent>()

    override val surrogates = CopyOnWriteArrayList<SurrogateResponse>()

    override val trackerCount: Int
        get() = trackingEvents.size

    override val majorNetworkCount: Int
        get() = trackingEvents.distinctBy { it.entity?.name }.count { it.entity?.isMajor ?: false }

    override val allTrackersBlocked: Boolean
        get() = trackingEvents.none { !it.blocked }

    private var fullSiteDetailsAvailable: Boolean = false

    private var currentProtection: PrivacyShield = PrivacyShield.UNKNOWN

    private val isHttps = https != HttpsStatus.NONE

    override var userAllowList: Boolean = false

    init {
        // httpsAutoUpgrade is not supported yet; for now, keep it equal to isHttps and don't penalise sites
        appCoroutineScope.launch {
            domain?.let { userAllowList = isWhitelisted(it) }
        }
    }

    override fun updatePrivacyData(sitePrivacyData: SitePrivacyData) {
        this.entity = sitePrivacyData.entity
        Timber.i("PDHy: fullSiteDetailsAvailable entity ${sitePrivacyData.entity} for $domain")
        fullSiteDetailsAvailable = true
    }

    private fun httpsStatus(): HttpsStatus {
        val uri = uri ?: return HttpsStatus.NONE

        if (uri.isHttps) {
            return if (hasHttpResources) HttpsStatus.MIXED else HttpsStatus.SECURE
        }

        return HttpsStatus.NONE
    }

    override fun surrogateDetected(surrogate: SurrogateResponse) {
        surrogates.add(surrogate)
    }

    override fun trackerDetected(event: TrackingEvent) {
        trackingEvents.add(event)
    }

    // TODO: remove when privacy dashboard is migrated
    override fun calculateGrades(): SiteGrades {
        return SiteGrades(PrivacyGrade.C, B)
    }

    override fun privacyProtection(): PrivacyShield {
        userAllowList = domain?.let { isWhitelisted(it) } ?: false
        if (userAllowList || !isHttps) return UNPROTECTED

        if (!fullSiteDetailsAvailable) {
            Timber.i("Shield: not fullSiteDetailsAvailable for $domain")
            Timber.i("Shield: entity is ${entity?.name} for $domain")
            return UNKNOWN
        }

        val isMajorNetwork = entity?.isMajor == true
        Timber.i("Shield: isMajor ${entity?.isMajor} prev ${entity?.prevalence} for $domain")

        if (isMajorNetwork) return UNPROTECTED
        return PROTECTED
    }

    @WorkerThread
    private fun isWhitelisted(domain: String): Boolean {
        return userWhitelistDao.contains(domain) || contentBlocking.isAnException(domain)
    }

    override var urlParametersRemoved: Boolean = false
}
