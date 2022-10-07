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

package com.duckduckgo.app.statistics.pixels

/**
 * This is a temporary class: At some point we will introduce a new class to log handled exception
 * or illegal states to be stored and send as offline pixels
 */
interface ExceptionPixel {
    fun sendExceptionPixel(pixelName: Pixel.PixelName, throwable: Throwable)
    fun sendExceptionPixel(pixelName: String, throwable: Throwable)
}
