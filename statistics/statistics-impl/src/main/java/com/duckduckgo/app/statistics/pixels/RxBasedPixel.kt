/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.statistics.pixels

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.api.PixelSender
import com.duckduckgo.app.statistics.api.PixelSender.SendPixelResult.PIXEL_IGNORED
import com.duckduckgo.app.statistics.api.PixelSender.SendPixelResult.PIXEL_SENT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import io.reactivex.schedulers.Schedulers
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RxBasedPixel @Inject constructor(
    private val pixelSender: PixelSender,
) : Pixel {
    override fun fire(
        pixel: Pixel.PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        fire(pixel.pixelName, parameters, encodedParameters, type)
    }

    @SuppressLint("CheckResult")
    override fun fire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        pixelSender
            .sendPixel(pixelName, parameters, encodedParameters, type)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { result ->
                    when (result) {
                        PIXEL_SENT -> logcat(VERBOSE) { "Pixel sent: $pixelName with params: $parameters $encodedParameters" }
                        PIXEL_IGNORED -> logcat(VERBOSE) { "Pixel ignored: $pixelName with params: $parameters $encodedParameters" }
                    }
                },
                {
                    logcat(WARN) { "Pixel failed: $pixelName with params: $parameters $encodedParameters: ${it.asLog()}" }
                },
            )
    }

    /**
     * Sends a pixel. If delivery fails, the pixel will be retried again in the future. As this
     * method stores the pixel to disk until successful delivery, check with privacy triage if the
     * pixel has additional parameters that they would want to validate.
     */
    override fun enqueueFire(
        pixel: Pixel.PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
    ) {
        enqueueFire(pixel.pixelName, parameters, encodedParameters)
    }

    @SuppressLint("CheckResult")
    /** See comment in {@link #enqueueFire(PixelName, Map<String, String>, Map<String, String>)}. */
    override fun enqueueFire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
    ) {
        pixelSender
            .enqueuePixel(pixelName, parameters, encodedParameters)
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    logcat(VERBOSE) { "Pixel enqueued: $pixelName with params: $parameters $encodedParameters" }
                },
                {
                    logcat(WARN) { "Pixel failed: $pixelName with params: $parameters $encodedParameters: ${it.asLog()}" }
                },
            )
    }
}
