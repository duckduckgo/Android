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
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.browser.certificates.BypassedSSLCertificatesRepository
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNKNOWN
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.browser.api.brokensite.BrokenSiteContext
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.isHttps
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.privacy.config.api.ContentBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class SiteMonitor(
    url: String,
    override var title: String?,
    override var upgradedHttps: Boolean = false,
    externalLaunch: Boolean,
    private val userAllowListRepository: UserAllowListRepository,
    private val contentBlocking: ContentBlocking,
    private val bypassedSSLCertificatesRepository: BypassedSSLCertificatesRepository,
    appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    brokenSiteContext: BrokenSiteContext,
    private val duckPlayer: DuckPlayer,
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

    override var sslError: Boolean = false

    override var isExternalLaunch = externalLaunch

    override var entity: Entity? = null

    override var certificate: SslCertificate? = null

    override val trackingEvents = CopyOnWriteArrayList<TrackingEvent>()
    override val errorCodeEvents = CopyOnWriteArrayList<String>()
    override val httpErrorCodeEvents = CopyOnWriteArrayList<Int>()

    override val surrogates = CopyOnWriteArrayList<SurrogateResponse>()

    override val trackerCount: Int
        get() = trackingEvents.count { it.status == TrackerStatus.BLOCKED }

    override val otherDomainsLoadedCount: Int
        get() = trackingEvents.asSequence()
            .filter { it.status == TrackerStatus.ALLOWED }
            .map { UriString.host(it.trackerUrl) }
            .distinct()
            .count()

    override val specialDomainsLoadedCount: Int
        get() = trackingEvents.asSequence()
            .filter { specialDomainTypes.contains(it.status) }
            .map { UriString.host(it.trackerUrl) }
            .distinct()
            .count()

    override val majorNetworkCount: Int
        get() = trackingEvents.distinctBy { it.entity?.name }.count { it.entity?.isMajor ?: false }

    override val allTrackersBlocked: Boolean
        get() = trackingEvents.none { it.status == TrackerStatus.USER_ALLOWED }

    private var fullSiteDetailsAvailable: Boolean = false

    private val isHttps = https != HttpsStatus.NONE

    override var userAllowList: Boolean = false

    init {
        // httpsAutoUpgrade is not supported yet; for now, keep it equal to isHttps and don't penalise sites
        appCoroutineScope.launch(dispatcherProvider.io()) {
            domain?.let { userAllowList = isAllowListed(it) }
        }
    }

    override fun updatePrivacyData(sitePrivacyData: SitePrivacyData) {
        this.entity = sitePrivacyData.entity
        Timber.i("fullSiteDetailsAvailable entity ${sitePrivacyData.entity} for $domain")
        fullSiteDetailsAvailable = true
    }

    private fun httpsStatus(): HttpsStatus {
        val uri = uri ?: return HttpsStatus.NONE

        if (uri.isHttps) {
            return if (hasHttpResources) HttpsStatus.MIXED else HttpsStatus.SECURE
        }

        return HttpsStatus.NONE
    }

    override fun resetErrors() {
        errorCodeEvents.clear()
        httpErrorCodeEvents.clear()
    }

    override fun surrogateDetected(surrogate: SurrogateResponse) {
        surrogates.add(surrogate)
    }

    override fun trackerDetected(event: TrackingEvent) {
        trackingEvents.add(event)
    }

    override fun onErrorDetected(error: String) {
        errorCodeEvents.add(error)
    }

    override fun onHttpErrorDetected(errorCode: Int) {
        httpErrorCodeEvents.add(errorCode)
    }

    override fun privacyProtection(): PrivacyShield {
        userAllowList = domain?.let { isAllowListed(it) } ?: false
        if (duckPlayer.isDuckPlayerUri(url)) return UNKNOWN
        if (userAllowList || !isHttps) return UNPROTECTED

        if (!fullSiteDetailsAvailable) {
            Timber.i("Shield: not fullSiteDetailsAvailable for $domain")
            Timber.i("Shield: entity is ${entity?.name} for $domain")
            return UNKNOWN
        }

        sslError = isSslCertificateBypassed(url)
        if (sslError) {
            Timber.i("Shield: site has certificate error")
            return UNPROTECTED
        }

        Timber.i("Shield: isMajor ${entity?.isMajor} prev ${entity?.prevalence} for $domain")
        return PROTECTED
    }

    override fun resetTrackingEvents() {
        trackingEvents.clear()
    }

    @WorkerThread
    private fun isAllowListed(domain: String): Boolean {
        return userAllowListRepository.isDomainInUserAllowList(domain) || contentBlocking.isAnException(domain)
    }

    private fun isSslCertificateBypassed(domain: String): Boolean {
        return bypassedSSLCertificatesRepository.contains(domain)
    }

    override var urlParametersRemoved: Boolean = false

    override var consentManaged: Boolean = false

    override var consentOptOutFailed: Boolean = false

    override var consentSelfTestFailed: Boolean = false

    override var consentCosmeticHide: Boolean? = false

    override var isDesktopMode: Boolean = false

    override var nextUrl: String = url

    override val realBrokenSiteContext: BrokenSiteContext = brokenSiteContext

    companion object {
        private val specialDomainTypes = setOf(
            TrackerStatus.AD_ALLOWED,
            TrackerStatus.SITE_BREAKAGE_ALLOWED,
            TrackerStatus.SAME_ENTITY_ALLOWED,
            TrackerStatus.USER_ALLOWED,
        )

        private val allowedDomainTypes = setOf(
            TrackerStatus.USER_ALLOWED,
            TrackerStatus.SAME_ENTITY_ALLOWED,
        )
    }
}
