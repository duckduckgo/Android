/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.user.agent.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.user.agent.store.UserAgentExceptionEntity
import com.duckduckgo.user.agent.store.UserAgentFeatureName
import com.duckduckgo.user.agent.store.UserAgentFeatureToggle
import com.duckduckgo.user.agent.store.UserAgentFeatureToggleRepository
import com.duckduckgo.user.agent.store.UserAgentRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class UserAgentPlugin @Inject constructor(
    private val userAgentRepository: UserAgentRepository,
    private val userAgentFeatureToggleRepository: UserAgentFeatureToggleRepository,
) : PrivacyFeaturePlugin {

    override fun store(
        featureName: String,
        jsonString: String,
    ): Boolean {
        val privacyFeature = userAgentFeatureValueOf(featureName) ?: return false
        if (privacyFeature.value == this.featureName) {
            val userAgentExceptions = mutableListOf<UserAgentExceptionEntity>()
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<UserAgentFeature> =
                moshi.adapter(UserAgentFeature::class.java)

            val userAgentFeature: UserAgentFeature? = jsonAdapter.fromJson(jsonString)
            val exceptionsList = userAgentFeature?.exceptions.orEmpty()

            exceptionsList.forEach {
                userAgentExceptions.add(UserAgentExceptionEntity(it.domain, it.reason.orEmpty()))
            }

            userAgentRepository.updateAll(userAgentExceptions)
            val isEnabled = userAgentFeature?.state == "enabled"
            userAgentFeatureToggleRepository.insert(UserAgentFeatureToggle(this.featureName, isEnabled, userAgentFeature?.minSupportedVersion))
            return true
        }
        return false
    }

    override val featureName: String = UserAgentFeatureName.UserAgent.value
}
