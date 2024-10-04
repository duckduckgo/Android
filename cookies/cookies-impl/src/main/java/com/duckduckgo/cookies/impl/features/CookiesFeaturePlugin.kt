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

package com.duckduckgo.cookies.impl.features

import com.duckduckgo.cookies.api.CookiesFeatureName
import com.duckduckgo.cookies.impl.cookiesFeatureValueOf
import com.duckduckgo.cookies.store.CookieEntity
import com.duckduckgo.cookies.store.CookieExceptionEntity
import com.duckduckgo.cookies.store.CookieNamesEntity
import com.duckduckgo.cookies.store.CookiesFeatureToggleRepository
import com.duckduckgo.cookies.store.CookiesFeatureToggles
import com.duckduckgo.cookies.store.CookiesRepository
import com.duckduckgo.cookies.store.FirstPartyCookiePolicyEntity
import com.duckduckgo.cookies.store.RealCookieRepository.Companion.DEFAULT_MAX_AGE
import com.duckduckgo.cookies.store.RealCookieRepository.Companion.DEFAULT_THRESHOLD
import com.duckduckgo.cookies.store.contentscopescripts.ContentScopeScriptsCookieRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class CookiesFeaturePlugin @Inject constructor(
    private val cookiesRepository: CookiesRepository,
    private val contentScopeScriptsCookieRepository: ContentScopeScriptsCookieRepository,
    private val cookiesFeatureToggleRepository: CookiesFeatureToggleRepository,
) : PrivacyFeaturePlugin {

    override fun store(featureName: String, jsonString: String): Boolean {
        val cookiesFeatureName = cookiesFeatureValueOf(featureName) ?: return false
        if (cookiesFeatureName.value == this.featureName) {
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<CookiesFeature> =
                moshi.adapter(CookiesFeature::class.java)

            val cookiesFeature: CookiesFeature? = jsonAdapter.fromJson(jsonString)

            val exceptions = cookiesFeature?.exceptions?.map {
                CookieExceptionEntity(domain = it.domain, reason = it.reason.orEmpty())
            }.orEmpty()

            val maxAge = cookiesFeature?.settings?.firstPartyCookiePolicy?.maxAge ?: DEFAULT_MAX_AGE
            val threshold = cookiesFeature?.settings?.firstPartyCookiePolicy?.threshold ?: DEFAULT_THRESHOLD
            val policy = FirstPartyCookiePolicyEntity(threshold = threshold, maxAge = maxAge)
            val thirdPartyCookieNames = cookiesFeature?.settings?.thirdPartyCookieNames?.map {
                CookieNamesEntity(name = it)
            }.orEmpty()

            cookiesRepository.updateAll(exceptions, policy, thirdPartyCookieNames)
            val isEnabled = cookiesFeature?.state == "enabled"
            cookiesFeatureToggleRepository.insert(
                CookiesFeatureToggles(cookiesFeatureName, isEnabled, cookiesFeature?.minSupportedVersion),
            )
            val entity = CookieEntity(json = jsonString)
            contentScopeScriptsCookieRepository.updateAll(cookieEntity = entity)
            return true
        }
        return false
    }

    override val featureName: String = CookiesFeatureName.Cookie.value
}
