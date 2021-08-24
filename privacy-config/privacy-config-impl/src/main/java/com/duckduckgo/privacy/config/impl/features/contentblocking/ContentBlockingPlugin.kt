/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.features.contentblocking

import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.ContentBlockingDao
import com.duckduckgo.privacy.config.store.ContentBlockingExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import javax.inject.Inject
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

@ContributesMultibinding(AppObjectGraph::class)
class ContentBlockingPlugin @Inject constructor(
    privacyConfigDatabase: PrivacyConfigDatabase
) : PrivacyFeaturePlugin {

    private val contentBlockingDao: ContentBlockingDao = privacyConfigDatabase.contentBlockingDao()
    private val privacyFeatureTogglesDao = privacyConfigDatabase.privacyFeatureTogglesDao()

    override fun store(name: String, jsonObject: JSONObject?): Boolean {
        if (name == featureName.value) {
            val contentBlockingExceptions = mutableListOf<ContentBlockingExceptionEntity>()
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<ContentBlockingFeature> =
                moshi.adapter(ContentBlockingFeature::class.java)

            val contentBlockingFeature: ContentBlockingFeature? = jsonAdapter.fromJson(jsonObject.toString())
            contentBlockingFeature?.exceptions?.map {
                contentBlockingExceptions.add(ContentBlockingExceptionEntity(it.domain, it.reason))
            }
            contentBlockingDao.updateAll(contentBlockingExceptions)
            val isEnabled = contentBlockingFeature?.state == "enabled"
            privacyFeatureTogglesDao.insert(PrivacyFeatureToggles(name, isEnabled))

            return true
        }
        return false
    }

    override val featureName: PrivacyFeatureName = PrivacyFeatureName.ContentBlockingFeatureName()
}
