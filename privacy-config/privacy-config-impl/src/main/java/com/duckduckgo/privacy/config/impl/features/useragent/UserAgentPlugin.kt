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
import com.duckduckgo.feature.toggles.api.FeatureName
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.features.privacyFeatureValueOf
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.UserAgentExceptionEntity
import com.duckduckgo.privacy.config.store.features.useragent.UserAgentRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

@ContributesMultibinding(AppScope::class)
class UserAgentPlugin @Inject constructor(
    private val userAgentRepository: UserAgentRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository
) : PrivacyFeaturePlugin {

    override fun store(
        name: FeatureName,
        jsonString: String
    ): Boolean {
        @Suppress("NAME_SHADOWING")
        val name = privacyFeatureValueOf(name.value)
        if (name == featureName) {
            val userAgentExceptions = mutableListOf<UserAgentExceptionEntity>()
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<UserAgentFeature> =
                moshi.adapter(UserAgentFeature::class.java)

            val userAgentFeature: UserAgentFeature? = jsonAdapter.fromJson(jsonString)
            val exceptionsList = userAgentFeature?.exceptions.orEmpty()
            val applicationList = userAgentFeature?.settings?.omitApplicationSites.orEmpty()
            val versionList = userAgentFeature?.settings?.omitVersionSites.orEmpty()

            val applicationAndVersionExceptionsList = applicationList intersect versionList
            val defaultExceptionList = (exceptionsList subtract applicationList) + (exceptionsList subtract versionList)
            val applicationExceptionList = applicationList subtract versionList subtract exceptionsList
            val versionExceptionList = versionList subtract applicationList subtract exceptionsList

            defaultExceptionList.forEach {
                userAgentExceptions.add(UserAgentExceptionEntity(it.domain, it.reason, omitApplication = false, omitVersion = false))
            }
            applicationAndVersionExceptionsList.forEach {
                userAgentExceptions.add(UserAgentExceptionEntity(it.domain, it.reason, omitApplication = true, omitVersion = true))
            }
            applicationExceptionList.forEach {
                userAgentExceptions.add(UserAgentExceptionEntity(it.domain, it.reason, omitApplication = true, omitVersion = false))
            }
            versionExceptionList.forEach {
                userAgentExceptions.add(UserAgentExceptionEntity(it.domain, it.reason, omitApplication = false, omitVersion = true))
            }

            userAgentRepository.updateAll(userAgentExceptions)
            val isEnabled = userAgentFeature?.state == "enabled"
            privacyFeatureTogglesRepository.insert(PrivacyFeatureToggles(name, isEnabled, userAgentFeature?.minSupportedVersion))
            return true
        }
        return false
    }

    override val featureName: PrivacyFeatureName = PrivacyFeatureName.UserAgentFeatureName
}
