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

package com.duckduckgo.app.statistics.api

import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import io.reactivex.Single

/**
 * Interface for sending anonymous analytics events (pixels). This interface is primarily intended for use within custom `OfflinePixel`
 * implementations. For general-purpose pixel sending, refer to the [com.duckduckgo.app.statistics.pixels.Pixel] interface.
 */
interface PixelSender {

    /**
     * Sends a pixel with the specified name and parameters.
     *
     * This method is safe to call from any thread, as the operation does not start until the returned [Single] is subscribed to.
     *
     * @param pixelName The name of the pixel event to be sent.
     * @param parameters A map of parameters to be included with the pixel event. These parameters are URL-encoded before being sent.
     * @param encodedParameters A map of parameters that are already URL-encoded. Use this when the parameters are pre-encoded.
     * @param type The type of pixel event to be sent.
     */
    fun sendPixel(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ): Single<SendPixelResult>

    /**
     * Sends a pixel with the specified name and parameters. Unlike the `sendPixel()` method, this method also persists the pixel in the local database,
     * allowing it to be retried in case of network issues or other failures.
     *
     * This method is safe to call from any thread, as the operation does not start until the returned [Single] is subscribed to.
     *
     * @param pixelName The name of the pixel event to be sent.
     * @param parameters A map of parameters to be included with the pixel event. These parameters are URL-encoded before being sent.
     * @param encodedParameters A map of parameters that are already URL-encoded. Use this when the parameters are pre-encoded.
     * @param type The type of pixel event to be sent.
     */
    fun enqueuePixel(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ): Single<EnqueuePixelResult>

    enum class SendPixelResult {
        PIXEL_SENT,
        PIXEL_IGNORED, // Daily or unique pixels may be ignored.
    }

    enum class EnqueuePixelResult {
        PIXEL_ENQUEUED,
        PIXEL_IGNORED, // Daily or unique pixels may be ignored.
    }
}
