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

package com.duckduckgo.app.statistics.store

/** Public interface for Offline Pixel Counter data store.
 *
 * This class was previously used to ensure we were able to send pixels when the application is about to be destroyed/closed.
 * We now have a preferred way to handle that usecase. See deprecation notes.
 *
 * */
@Deprecated(
    message = "We will no longer evolve this class. Instead please create your own datastore to persist pixels locally," +
        "and provide your own implementation of {@link OfflinePixel}. You can also use {@link Pixel.enqueuePixel} which is less flexible but simpler.",
    level = DeprecationLevel.WARNING
)
interface OfflinePixelCountDataStore {
    var applicationCrashCount: Int
    var webRendererGoneCrashCount: Int
    var webRendererGoneKilledCount: Int
    var cookieDatabaseNotFoundCount: Int
    var cookieDatabaseOpenErrorCount: Int
    var cookieDatabaseCorruptedCount: Int
    var cookieDatabaseDeleteErrorCount: Int
}
