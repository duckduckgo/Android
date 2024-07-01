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

package com.duckduckgo.duckplayer.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.impl.DuckPlayerFeatureRepository.UserValues
import com.duckduckgo.duckplayer.impl.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.impl.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.impl.PrivatePlayerMode.Enabled
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDuckPlayer @Inject constructor(
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    private val pixel: Pixel,
) : DuckPlayer {

    override fun setUserValues(
        overlayInteracted: Boolean,
        privatePlayerMode: String,
    ) {
        val playerMode = when {
            privatePlayerMode.contains("disabled") -> Disabled
            privatePlayerMode.contains("enabled") -> Enabled
            else -> AlwaysAsk
        }
        duckPlayerFeatureRepository.setUserValues(UserValues(overlayInteracted, playerMode))
    }

    override suspend fun getUserValues(): DuckPlayer.UserValues {
        return duckPlayerFeatureRepository.getUserValues().let {
            DuckPlayer.UserValues(it.overlayInteracted, it.privatePlayerMode.value)
        }
    }

    override fun sendDuckPlayerPixel(
        pixelName: String,
        pixelData: Map<String, String>,
    ) {
        val androidPixelName = "m_${pixelName.replace('.', '_')}"
        pixel.fire(androidPixelName, pixelData)
    }
}
