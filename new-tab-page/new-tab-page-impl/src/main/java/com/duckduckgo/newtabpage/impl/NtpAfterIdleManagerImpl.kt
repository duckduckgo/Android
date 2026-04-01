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

package com.duckduckgo.newtabpage.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.newtabpage.impl.pixels.HatchPixels
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.BAR_USED_FROM_NTP_AFTER_IDLE
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.BAR_USED_FROM_NTP_AFTER_IDLE_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.BAR_USED_FROM_NTP_USER_INITIATED
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.BAR_USED_FROM_NTP_USER_INITIATED_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.NTP_SHOWN_AFTER_IDLE
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.NTP_SHOWN_AFTER_IDLE_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.NTP_SHOWN_USER_INITIATED
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.NTP_SHOWN_USER_INITIATED_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixels
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NtpAfterIdleManagerImpl @Inject constructor(
    private val pixel: Pixel,
    private val hatchPixels: HatchPixels,
) : NtpAfterIdleManager {

    private val afterIdle = AtomicBoolean(false)

    override fun wasAfterIdle(): Boolean = afterIdle.get()

    override fun onNtpShownAfterIdle() {
        afterIdle.set(true)
        pixel.fire(NTP_SHOWN_AFTER_IDLE, type = Count)
        pixel.fire(NTP_SHOWN_AFTER_IDLE_DAILY, type = Daily())
    }

    override fun onNtpShownUserInitiated() {
        afterIdle.set(false)
        pixel.fire(NTP_SHOWN_USER_INITIATED, type = Count)
        pixel.fire(NTP_SHOWN_USER_INITIATED_DAILY, type = Daily())
    }

    override fun fireReturnToPageTapped() {
        hatchPixels.fireReturnToPageTapped(wasAfterIdle())
    }

    override fun fireBarUsedFromNtp() {
        if (wasAfterIdle()) {
            pixel.fire(BAR_USED_FROM_NTP_AFTER_IDLE, type = Count)
            pixel.fire(BAR_USED_FROM_NTP_AFTER_IDLE_DAILY, type = Daily())
        } else {
            pixel.fire(BAR_USED_FROM_NTP_USER_INITIATED, type = Count)
            pixel.fire(BAR_USED_FROM_NTP_USER_INITIATED_DAILY, type = Daily())
        }
    }

    override fun fireTimeoutSelected(seconds: Long) {
        NtpAfterIdlePixels.timeoutPixelsForSeconds(seconds)?.let { (count, daily) ->
            pixel.fire(count, type = Count)
            pixel.fire(daily, type = Daily())
        }
    }
}
