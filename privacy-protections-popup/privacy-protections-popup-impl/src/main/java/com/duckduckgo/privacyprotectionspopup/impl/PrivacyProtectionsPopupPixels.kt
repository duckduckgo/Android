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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentPixelParamsProvider
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.EXPERIMENT_VARIANT_ASSIGNED
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface PrivacyProtectionsPopupPixels {
    fun reportExperimentVariantAssigned()
}

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupPixelsImpl @Inject constructor(
    private val pixelSender: Pixel,
    private val paramsProvider: PrivacyProtectionsPopupExperimentPixelParamsProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PrivacyProtectionsPopupPixels {

    override fun reportExperimentVariantAssigned() {
        appCoroutineScope.launch {
            fire(EXPERIMENT_VARIANT_ASSIGNED)
        }
    }

    private suspend fun fire(pixel: PrivacyProtectionsPopupPixelName) {
        pixelSender.fire(
            pixel = pixel,
            parameters = paramsProvider.getPixelParams(),
            type = pixel.type,
        )
    }
}
