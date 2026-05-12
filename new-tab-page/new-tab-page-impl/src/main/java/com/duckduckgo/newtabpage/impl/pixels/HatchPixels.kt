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

package com.duckduckgo.newtabpage.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.RETURN_TO_PAGE_TAPPED_AFTER_IDLE
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.RETURN_TO_PAGE_TAPPED_AFTER_IDLE_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.RETURN_TO_PAGE_TAPPED_USER_INITIATED
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.RETURN_TO_PAGE_TAPPED_USER_INITIATED_DAILY
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface HatchPixels {
    fun fireReturnToPageTapped(afterIdle: Boolean)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealHatchPixels @Inject constructor(
    private val pixel: Pixel,
) : HatchPixels {

    override fun fireReturnToPageTapped(afterIdle: Boolean) {
        if (afterIdle) {
            pixel.fire(RETURN_TO_PAGE_TAPPED_AFTER_IDLE, type = Count)
            pixel.fire(RETURN_TO_PAGE_TAPPED_AFTER_IDLE_DAILY, type = Daily())
        } else {
            pixel.fire(RETURN_TO_PAGE_TAPPED_USER_INITIATED, type = Count)
            pixel.fire(RETURN_TO_PAGE_TAPPED_USER_INITIATED_DAILY, type = Daily())
        }
    }
}
