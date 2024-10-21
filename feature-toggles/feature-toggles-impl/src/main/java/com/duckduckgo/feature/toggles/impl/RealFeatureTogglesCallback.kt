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

package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.impl.FeatureTogglesPixelName.EXPERIMENT_ENROLLMENT
import com.duckduckgo.feature.toggles.internal.api.FeatureTogglesCallback
import com.squareup.anvil.annotations.ContributesBinding
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import okio.ByteString.Companion.encode

@ContributesBinding(AppScope::class)
class RealFeatureTogglesCallback @Inject constructor(
    private val pixel: Pixel,
) : FeatureTogglesCallback {
    override fun onCohortAssigned(
        experimentName: String,
        cohortName: String,
        enrollmentDate: String,
    ) {
        val parsedDate = ZonedDateTime.parse(enrollmentDate).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val params = mapOf("enrollmentDate" to parsedDate)
        val pixelName = getPixelName(experimentName, cohortName)
        val tag = "${pixelName}_$params".encode().md5().hex()
        pixel.fire(pixelName = pixelName, parameters = params, type = Pixel.PixelType.Unique(tag = tag))
    }

    private fun getPixelName(
        experimentName: String,
        cohortName: String,
    ): String {
        return "${EXPERIMENT_ENROLLMENT.pixelName}_${experimentName}_$cohortName"
    }
}

internal enum class FeatureTogglesPixelName(override val pixelName: String) : Pixel.PixelName {
    EXPERIMENT_ENROLLMENT("experiment_enroll"),
}
