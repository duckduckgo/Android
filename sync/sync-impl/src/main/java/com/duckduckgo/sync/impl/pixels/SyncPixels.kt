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

package com.duckduckgo.sync.impl.pixels

import android.content.Context
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.stats.SyncStatsRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SyncPixels {
    fun fireStatsPixel()
}

@ContributesBinding(AppScope::class)
class RealSyncPixels @Inject constructor(
    private val pixel: Pixel,
    private val context: Context,
    private val statsRepository: SyncStatsRepository
) : SyncPixels {
    override fun fireStatsPixel() {



    }
}

enum class SyncPixelName(override val pixelName: String): Pixel.PixelName {

}

object SyncPixelValues {

}

object SyncPixelParameters {

}
