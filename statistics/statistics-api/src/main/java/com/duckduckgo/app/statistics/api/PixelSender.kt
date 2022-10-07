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

package com.duckduckgo.app.statistics.api

import io.reactivex.Completable

/**
 * Public interface for Pixel sender.
 *
 * This interface offers a way to send or enqueue a Pixel with more control than using {@link Pixel}.
 * Use this api if you want to have visibility on the action result. For example: to ensure if action succeed or failed.
 */
interface PixelSender {

    /**
     * Fires a pixel.
     */
    fun sendPixel(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>
    ): Completable

    /**
     * Enqueues a pixel.
     */
    fun enqueuePixel(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>
    ): Completable
}
