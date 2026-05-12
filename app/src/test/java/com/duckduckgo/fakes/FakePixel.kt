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

package com.duckduckgo.fakes

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType

internal class FakePixel : Pixel {

    val firedPixels = mutableListOf<String>()

    override fun fire(
        pixel: PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        firedPixels.add(pixel.pixelName)
    }

    override fun fire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        firedPixels.add(pixelName)
    }

    override fun enqueueFire(
        pixel: PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        firedPixels.add(pixel.pixelName)
    }

    override fun enqueueFire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        firedPixels.add(pixelName)
    }
}
