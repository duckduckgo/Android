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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.di.scopes.AppScope
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import javax.inject.Qualifier

@Qualifier
annotation class TrackerDetectionMoshi

@BindingContainer
@ContributesTo(AppScope::class)
object TrackerDetectionJsonModule {

    /**
     * Room instantiates type converters itself, so the tracker-detection adapters have to be
     * reachable without going through the Dagger graph as well as through it.
     */
    val moshi: Moshi = Moshi.Builder()
        .add(ActionJsonAdapter())
        .build()

    @Provides
    @SingleIn(AppScope::class)
    @TrackerDetectionMoshi
    fun trackerDetectionMoshi(): Moshi = moshi
}
