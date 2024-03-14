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

package com.duckduckgo.app.pixels

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.lifecycle.VpnProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class ProcessCreationMainPixelSender @Inject constructor(
    private val pixel: Pixel,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        pixel.fire(AppPixelName.PROCESS_CREATED_MAIN)
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = VpnProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class ProcessCreationVpnPixelSender @Inject constructor(
    private val pixel: Pixel,
) : VpnProcessLifecycleObserver {

    override fun onVpnProcessCreated() {
        pixel.fire(AppPixelName.PROCESS_CREATED_VPN)
    }
}
