/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.eventhub.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import java.net.URLDecoder

/** Records every pixel the hub requests, so a spec test can assert the complete set. */
internal class RecordingPixel : Pixel {

    data class Request(val name: String, val parameters: Map<String, String>, val encodedParameters: Map<String, String>) {
        /** Every parameter except `attributionPeriod`, with data parameters percent-decoded back to their compact JSON. */
        val values: Map<String, String>
            get() = parameters.minus(RealEventHubPixelManager.PARAM_ATTRIBUTION_PERIOD) +
                encodedParameters.mapValues { (_, value) -> URLDecoder.decode(value, Charsets.UTF_8) }
    }

    val requests = mutableListOf<Request>()

    override fun enqueueFire(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>, type: PixelType) {
        requests += Request(pixelName, parameters, encodedParameters)
    }

    override fun enqueueFire(pixel: PixelName, parameters: Map<String, String>, encodedParameters: Map<String, String>, type: PixelType) =
        unexpected(pixel.pixelName)

    override fun fire(pixel: PixelName, parameters: Map<String, String>, encodedParameters: Map<String, String>, type: PixelType) =
        unexpected(pixel.pixelName)

    override fun fire(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>, type: PixelType) =
        unexpected(pixelName)

    private fun unexpected(pixelName: String): Nothing = throw AssertionError("Event Hub pixels are enqueued, but $pixelName was fired directly")
}
