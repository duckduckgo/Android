/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.integration.fakes

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.pir.impl.pixels.PirPixel

/**
 * Fake implementation of Pixel for testing that tracts fired pixels, but does not actually send them.
 */
class FakePixel : Pixel {

    private val firedPixels = mutableListOf<FiredPixel>()

    override fun fire(
        pixel: PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        fire(pixel.pixelName, parameters, encodedParameters, type)
    }

    override fun fire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        firedPixels.add(
            FiredPixel(
                pixelName = pixelName,
                parameters = parameters,
                encodedParameters = encodedParameters,
                type = type,
            ),
        )
    }

    override fun enqueueFire(
        pixel: PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        enqueueFire(pixel.pixelName, parameters, encodedParameters)
    }

    override fun enqueueFire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        firedPixels.add(
            FiredPixel(
                pixelName = pixelName,
                parameters = parameters,
                encodedParameters = encodedParameters,
                type = PixelType.Count,
            ),
        )
    }

    fun wasPixelFired(pixel: PirPixel): Boolean {
        return firedPixels.any { it.pixelName.startsWith(pixel.baseName) }
    }

    fun clear() {
        firedPixels.clear()
    }

    private data class FiredPixel(
        val pixelName: String,
        val parameters: Map<String, String>,
        val encodedParameters: Map<String, String>,
        val type: PixelType,
    )
}
