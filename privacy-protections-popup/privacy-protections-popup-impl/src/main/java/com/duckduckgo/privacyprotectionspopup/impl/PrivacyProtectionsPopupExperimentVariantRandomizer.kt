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

package com.duckduckgo.privacyprotectionspopup.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupExperimentVariant.CONTROL
import com.squareup.anvil.annotations.ContributesBinding
import org.apache.commons.math3.random.RandomDataGenerator
import javax.inject.Inject

interface PrivacyProtectionsPopupExperimentVariantRandomizer {
    fun getRandomVariant(): PrivacyProtectionsPopupExperimentVariant
}

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupExperimentVariantRandomizerImpl @Inject constructor(
    private val buildConfig: AppBuildConfig,
) : PrivacyProtectionsPopupExperimentVariantRandomizer {

    override fun getRandomVariant(): PrivacyProtectionsPopupExperimentVariant {
        if (buildConfig.isDefaultVariantForced) return CONTROL

        val variants = PrivacyProtectionsPopupExperimentVariant.entries
        val randomIndex = RandomDataGenerator().nextInt(0, variants.lastIndex)
        return variants[randomIndex]
    }
}
