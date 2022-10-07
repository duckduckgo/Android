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
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import timber.log.Timber

class RxBasedPixel @Inject constructor(private val pixelSender: PixelSender) : Pixel {

    override fun fire(
        pixel: Pixel.PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>
    ) {
        fire(pixel.pixelName, parameters, encodedParameters)
    }

    @SuppressLint("CheckResult")
    override fun fire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>
    ) {
        pixelSender
            .sendPixel(pixelName, parameters, encodedParameters)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { Timber.v("Pixel sent: $pixelName with params: $parameters $encodedParameters") },
                {
                    Timber.w(
                        it, "Pixel failed: $pixelName with params: $parameters $encodedParameters"
                    )
                }
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
        encodedParameters: Map<String, String>
    ) {
        enqueueFire(pixel.pixelName, parameters, encodedParameters)
    }

    @SuppressLint("CheckResult")
    /** See comment in {@link #enqueueFire(PixelName, Map<String, String>, Map<String, String>)}. */
    override fun enqueueFire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>
    ) {
        pixelSender
            .enqueuePixel(pixelName, parameters, encodedParameters)
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    Timber.v(
                        "Pixel enqueued: $pixelName with params: $parameters $encodedParameters"
                    )
                },
                {
                    Timber.w(
                        it, "Pixel failed: $pixelName with params: $parameters $encodedParameters"
                    )
                }
            )
    }
}
