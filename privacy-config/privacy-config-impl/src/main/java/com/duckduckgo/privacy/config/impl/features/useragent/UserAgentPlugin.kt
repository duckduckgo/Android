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

package com.duckduckgo.privacy.config.impl.features.useragent

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.impl.features.privacyFeatureValueOf
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.UserAgentExceptionEntity
import com.duckduckgo.privacy.config.store.UserAgentSitesEntity
import com.duckduckgo.privacy.config.store.UserAgentStatesEntity
import com.duckduckgo.privacy.config.store.UserAgentVersionsEntity
import com.duckduckgo.privacy.config.store.features.useragent.UserAgentRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class UserAgentPlugin @Inject constructor(
    private val userAgentRepository: UserAgentRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository,
) : PrivacyFeaturePlugin {

    override fun store(
        featureName: String,
        jsonString: String,
    ): Boolean {
        @Suppress("NAME_SHADOWING")
        val privacyFeature = privacyFeatureValueOf(featureName) ?: return false
        if (privacyFeature.value == this.featureName) {
            val userAgentExceptions = mutableListOf<UserAgentExceptionEntity>()
            val userAgentSites = mutableListOf<UserAgentSitesEntity>()
            val userAgentVersions = mutableListOf<UserAgentVersionsEntity>()
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<UserAgentFeature> =
                moshi.adapter(UserAgentFeature::class.java)

            val userAgentFeature: UserAgentFeature? = jsonAdapter.fromJson(jsonString)
            val exceptionsList = userAgentFeature?.exceptions.orEmpty()
            val applicationList = userAgentFeature?.settings?.omitApplicationSites.orEmpty()
            val versionList = userAgentFeature?.settings?.omitVersionSites.orEmpty()
            val ddgDefaultSitesList = userAgentFeature?.settings?.ddgDefaultSites.orEmpty()
            val ddgFixedSitesList = userAgentFeature?.settings?.ddgFixedSites.orEmpty()
            val closestUserAgentVersionList = userAgentFeature?.settings?.closestUserAgent?.versions.orEmpty()
            val ddgFixedUserAgentVersionList = userAgentFeature?.settings?.ddgFixedUserAgent?.versions.orEmpty()

            val applicationAndVersionExceptionsList = applicationList intersect versionList
            val defaultExceptionList = (exceptionsList subtract applicationList) + (exceptionsList subtract versionList)
            val applicationExceptionList = applicationList subtract versionList subtract exceptionsList
            val versionExceptionList = versionList subtract applicationList subtract exceptionsList

            defaultExceptionList.forEach {
                userAgentExceptions.add(UserAgentExceptionEntity(it.domain, it.reason.orEmpty(), omitApplication = false, omitVersion = false))
            }
            applicationAndVersionExceptionsList.forEach {
                userAgentExceptions.add(UserAgentExceptionEntity(it.domain, it.reason.orEmpty(), omitApplication = true, omitVersion = true))
            }
            applicationExceptionList.forEach {
                userAgentExceptions.add(UserAgentExceptionEntity(it.domain, it.reason.orEmpty(), omitApplication = true, omitVersion = false))
            }
            versionExceptionList.forEach {
                userAgentExceptions.add(UserAgentExceptionEntity(it.domain, it.reason.orEmpty(), omitApplication = false, omitVersion = true))
            }
            ddgDefaultSitesList.forEach {
                userAgentSites.add(UserAgentSitesEntity(it.domain, it.reason.orEmpty(), ddgDefaultSite = true, ddgFixedSite = false))
            }
            ddgFixedSitesList.forEach {
                userAgentSites.add(UserAgentSitesEntity(it.domain, it.reason.orEmpty(), ddgDefaultSite = false, ddgFixedSite = true))
            }
            closestUserAgentVersionList.forEach {
                userAgentVersions.add(UserAgentVersionsEntity(it, closestUserAgent = true, ddgFixedUserAgent = false))
            }
            ddgFixedUserAgentVersionList.forEach {
                userAgentVersions.add(UserAgentVersionsEntity(it, closestUserAgent = false, ddgFixedUserAgent = true))
            }

            val userAgentStates = userAgentFeature?.settings?.let { settings ->
                if (settings.defaultPolicy == null || settings.closestUserAgent == null || settings.ddgFixedUserAgent == null) {
                    null
                } else {
                    UserAgentStatesEntity(
                        defaultPolicy = settings.defaultPolicy,
                        closestUserAgent = settings.closestUserAgent.state == "enabled",
                        ddgFixedUserAgent = settings.ddgFixedUserAgent.state == "enabled",
                    )
                }
            }

            userAgentRepository.updateAll(userAgentExceptions, userAgentSites, userAgentStates, userAgentVersions)
            val isEnabled = userAgentFeature?.state == "enabled"
            privacyFeatureTogglesRepository.insert(PrivacyFeatureToggles(this.featureName, isEnabled, userAgentFeature?.minSupportedVersion))
            return true
        }
        return false
    }

    override val featureName: String = PrivacyFeatureName.UserAgentFeatureName.value
}
