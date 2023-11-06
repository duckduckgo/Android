/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.brokensite.api

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.absoluteString
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.brokensite.api.BrokenSiteLastSentReport
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyConfig
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

interface BrokenSiteSender {
    fun submitBrokenSiteFeedback(brokenSite: BrokenSite)
}

@ContributesBinding(AppScope::class)
class BrokenSiteSubmitter @Inject constructor(
    private val statisticsStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val tdsMetadataDao: TdsMetadataDao,
    private val gpc: Gpc,
    private val featureToggle: FeatureToggle,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val privacyConfig: PrivacyConfig,
    private val userAllowListRepository: UserAllowListRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
    private val contentBlocking: ContentBlocking,
    private val brokenSiteLastSentReport: BrokenSiteLastSentReport,
) : BrokenSiteSender {

    override fun submitBrokenSiteFeedback(brokenSite: BrokenSite) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val isGpcEnabled = (featureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName.value) && gpc.isEnabled()).toString()
            val absoluteUrl = Uri.parse(brokenSite.siteUrl).absoluteString

            val domain = brokenSite.siteUrl.toUri().domain()
            val protectionsState = !userAllowListRepository.isDomainInUserAllowList(domain) &&
                !unprotectedTemporary.isAnException(brokenSite.siteUrl) &&
                featureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value) &&
                !contentBlocking.isAnException(brokenSite.siteUrl)

            val params = mutableMapOf(
                CATEGORY_KEY to brokenSite.category.orEmpty(),
                DESCRIPTION_KEY to brokenSite.description.orEmpty(),
                SITE_URL_KEY to absoluteUrl,
                UPGRADED_HTTPS_KEY to brokenSite.upgradeHttps.toString(),
                TDS_ETAG_KEY to tdsMetadataDao.eTag().orEmpty(),
                APP_VERSION_KEY to appBuildConfig.versionName,
                ATB_KEY to atbWithVariant(),
                OS_KEY to appBuildConfig.sdkInt.toString(),
                MANUFACTURER_KEY to appBuildConfig.manufacturer,
                MODEL_KEY to appBuildConfig.model,
                WEBVIEW_VERSION_KEY to brokenSite.webViewVersion,
                SITE_TYPE_KEY to brokenSite.siteType,
                GPC to isGpcEnabled,
                URL_PARAMETERS_REMOVED to brokenSite.urlParametersRemoved.toBinaryString(),
                CONSENT_MANAGED to brokenSite.consentManaged.toBinaryString(),
                CONSENT_OPT_OUT_FAILED to brokenSite.consentOptOutFailed.toBinaryString(),
                CONSENT_SELF_TEST_FAILED to brokenSite.consentSelfTestFailed.toBinaryString(),
                REMOTE_CONFIG_VERSION to privacyConfig.privacyConfigData()?.version.orEmpty(),
                REMOTE_CONFIG_ETAG to privacyConfig.privacyConfigData()?.eTag.orEmpty(),
                ERROR_CODES_KEY to brokenSite.errorCodes,
                HTTP_ERROR_CODES_KEY to brokenSite.httpErrorCodes,
                PROTECTIONS_STATE to protectionsState.toBinaryString(),
            )

            val lastSentDay = brokenSiteLastSentReport.getLastSentDay(domain.orEmpty())
            if (lastSentDay != null) {
                params[LAST_SENT_DAY] = lastSentDay
            }

            if (appBuildConfig.deviceLocale.language == Locale.ENGLISH.language) {
                params[LOGIN_SITE] = brokenSite.loginSite.orEmpty()
            }

            val encodedParams = mapOf(
                BLOCKED_TRACKERS_KEY to brokenSite.blockedTrackers,
                SURROGATES_KEY to brokenSite.surrogates,
            )
            runCatching {
                pixel.fire(AppPixelName.BROKEN_SITE_REPORT.pixelName, params.toMap(), encodedParams)
            }
                .onSuccess {
                    Timber.v("Feedback submission succeeded")
                    if (!domain.isNullOrEmpty()) {
                        brokenSiteLastSentReport.setLastSentDay(domain)
                    }
                }
                .onFailure { Timber.w(it, "Feedback submission failed") }
        }
    }

    private fun atbWithVariant(): String {
        return statisticsStore.atb?.formatWithVariant(variantManager.getVariant()).orEmpty()
    }

    companion object {
        private const val CATEGORY_KEY = "category"
        private const val DESCRIPTION_KEY = "description"
        private const val SITE_URL_KEY = "siteUrl"
        private const val UPGRADED_HTTPS_KEY = "upgradedHttps"
        private const val TDS_ETAG_KEY = "tds"
        private const val BLOCKED_TRACKERS_KEY = "blockedTrackers"
        private const val SURROGATES_KEY = "surrogates"
        private const val APP_VERSION_KEY = "appVersion"
        private const val ATB_KEY = "atb"
        private const val OS_KEY = "os"
        private const val MANUFACTURER_KEY = "manufacturer"
        private const val MODEL_KEY = "model"
        private const val WEBVIEW_VERSION_KEY = "wvVersion"
        private const val SITE_TYPE_KEY = "siteType"
        private const val GPC = "gpc"
        private const val URL_PARAMETERS_REMOVED = "urlParametersRemoved"
        private const val CONSENT_MANAGED = "consentManaged"
        private const val CONSENT_OPT_OUT_FAILED = "consentOptoutFailed"
        private const val CONSENT_SELF_TEST_FAILED = "consentSelftestFailed"
        private const val REMOTE_CONFIG_VERSION = "remoteConfigVersion"
        private const val REMOTE_CONFIG_ETAG = "remoteConfigEtag"
        private const val ERROR_CODES_KEY = "errorDescriptions"
        private const val HTTP_ERROR_CODES_KEY = "httpErrorCodes"
        private const val PROTECTIONS_STATE = "protectionsState"
        private const val LAST_SENT_DAY = "lastSentDay"
        private const val LOGIN_SITE = "loginSite"
    }
}

fun Boolean.toBinaryString(): String = if (this) "1" else "0"
