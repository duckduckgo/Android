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

package com.duckduckgo.fingerprintprotection.impl.features.fingerprintingscreensize

import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.fingerprintprotection.api.FingerprintProtectionFeatureName
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingscreensize.FingerprintingScreenSizeRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class FingerprintingScreenSizeContentScopeConfigPlugin @Inject constructor(
    private val fingerprintingScreenSizeRepository: FingerprintingScreenSizeRepository,
) : ContentScopeConfigPlugin {

    override fun config(): String {
        val featureName = FingerprintProtectionFeatureName.FingerprintingScreenSize.value
        val config = fingerprintingScreenSizeRepository.fingerprintingScreenSizeEntity.json
        return "\"$featureName\":$config"
    }

    override fun preferences(): String? {
        return null
    }
}
