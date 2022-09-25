/*
 * Copyright (c) 2021 DuckDuckGo
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
 * Implement this interface and return a multibinding when you want to schedule the sending
 * of pixels.
 * The use case is generally when you have a DB with some pixels to be sent periodically.
 * You can use this ready-made infrastructure instead of creating your own worker etc.
 *
 * Example:
 * <pre><code>
 * @ContributesMultibinding(AppScope::class)
 * class MyOfflinePixelSender @Inject constructor(...) {
 *   override fun send(): Completable {
 *     return defer {
 *       // get stored data
 *       // send pixel(s)
 *     }
 *   }
 * }
 * </code></pre>
 */
interface OfflinePixel {
    fun send(): Completable
}
