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
import com.duckduckgo.browser.api.BrowserLifecycleObserver
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
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = NtpAfterIdleManager::class)
@ContributesMultibinding(AppScope::class, boundType = BrowserLifecycleObserver::class)
class NtpAfterIdleManagerImpl @Inject constructor(
    private val pixel: Pixel,
    private val hatchPixels: HatchPixels,
) : NtpAfterIdleManager, BrowserLifecycleObserver {

    private val pendingAfterIdle = AtomicBoolean(false)
    private val _isAfterIdleReturn = MutableStateFlow(false)
    override val isAfterIdleReturn: StateFlow<Boolean> = _isAfterIdleReturn.asStateFlow()

    override fun onOpen(isFreshLaunch: Boolean) {
        // pendingAfterIdle is intentionally NOT reset here. FirstScreenHandlerImpl.onOpen
        // synchronously calls onIdleReturnTriggered() when appropriate, and BrowserLifecycleObserver
        // callbacks run in a non-deterministic multibinding order — clearing here could wipe a
        // just-set value. The flag is consumed by onNtpShown() (getAndSet(false)), and any residual
        // stale state is cleared in onClose() when the app backgrounds.
        _isAfterIdleReturn.value = false
    }

    override fun onClose() {
        pendingAfterIdle.set(false)
        _isAfterIdleReturn.value = false
    }

    override fun onIdleReturnTriggered() {
        pendingAfterIdle.set(true)
    }

    override fun onNtpShown() {
        val wasAfterIdle = pendingAfterIdle.getAndSet(false)
        _isAfterIdleReturn.value = wasAfterIdle
        if (wasAfterIdle) {
            pixel.fire(NTP_SHOWN_AFTER_IDLE, type = Count)
            pixel.fire(NTP_SHOWN_AFTER_IDLE_DAILY, type = Daily())
        } else {
            pixel.fire(NTP_SHOWN_USER_INITIATED, type = Count)
            pixel.fire(NTP_SHOWN_USER_INITIATED_DAILY, type = Daily())
        }
    }

    override fun onReturnToPageTapped() {
        hatchPixels.fireReturnToPageTapped(_isAfterIdleReturn.value)
    }

    override fun onNtpSearchSubmitted() {
        if (_isAfterIdleReturn.value) {
            pixel.fire(BAR_USED_FROM_NTP_AFTER_IDLE, type = Count)
            pixel.fire(BAR_USED_FROM_NTP_AFTER_IDLE_DAILY, type = Daily())
        } else {
            pixel.fire(BAR_USED_FROM_NTP_USER_INITIATED, type = Count)
            pixel.fire(BAR_USED_FROM_NTP_USER_INITIATED_DAILY, type = Daily())
        }
    }

    override fun onIdleTimeoutSelected(seconds: Long) {
        NtpAfterIdlePixels.timeoutPixelsForSeconds(seconds)?.let { (count, daily) ->
            pixel.fire(count, type = Count)
            pixel.fire(daily, type = Daily())
        }
    }
}
