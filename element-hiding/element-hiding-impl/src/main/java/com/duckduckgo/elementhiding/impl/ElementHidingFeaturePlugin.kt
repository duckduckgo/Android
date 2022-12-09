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

package com.duckduckgo.elementhiding.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.elementhiding.store.ElementHidingEntity
import com.duckduckgo.elementhiding.store.ElementHidingRepository
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ElementHidingFeaturePlugin @Inject constructor(
    private val elementHidingRepository: ElementHidingRepository,
) : PrivacyFeaturePlugin {

    override fun store(featureName: String, jsonString: String): Boolean {
        val elementHidingFeatureName = elementHidingFeatureValueOf(featureName) ?: return false
        if (elementHidingFeatureName.value == this.featureName) {
            val entity = ElementHidingEntity(json = jsonString)
            elementHidingRepository.updateAll(elementHidingEntity = entity)
            return true
        }
        return false
    }

    override val featureName: String = ElementHidingFeatureName.ElementHiding.value
}
