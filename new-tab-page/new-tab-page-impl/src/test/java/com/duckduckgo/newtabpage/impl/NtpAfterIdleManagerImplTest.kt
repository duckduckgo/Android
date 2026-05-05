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
import com.duckduckgo.newtabpage.impl.pixels.HatchPixels
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.BAR_USED_FROM_NTP_AFTER_IDLE
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.BAR_USED_FROM_NTP_AFTER_IDLE_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.BAR_USED_FROM_NTP_USER_INITIATED
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.BAR_USED_FROM_NTP_USER_INITIATED_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.NTP_SHOWN_AFTER_IDLE
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.NTP_SHOWN_AFTER_IDLE_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.NTP_SHOWN_USER_INITIATED
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.NTP_SHOWN_USER_INITIATED_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.TIMEOUT_SELECTED_60
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.TIMEOUT_SELECTED_60_DAILY
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.TIMEOUT_SELECTED_ALWAYS
import com.duckduckgo.newtabpage.impl.pixels.NtpAfterIdlePixelName.TIMEOUT_SELECTED_ALWAYS_DAILY
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class NtpAfterIdleManagerImplTest {

    private val pixel: Pixel = mock()
    private val hatchPixels: HatchPixels = mock()

    private lateinit var testee: NtpAfterIdleManagerImpl

    @Before
    fun setup() {
        testee = NtpAfterIdleManagerImpl(pixel, hatchPixels)
    }

    // --- onNtpShown classification ---

    @Test
    fun whenOnNtpShownWithoutPriorTriggerThenFiresUserInitiatedShownPixels() {
        testee.onNtpShown()

        verify(pixel).fire(NTP_SHOWN_USER_INITIATED, type = Count)
        verify(pixel).fire(NTP_SHOWN_USER_INITIATED_DAILY, type = Daily())
        verify(pixel, never()).fire(NTP_SHOWN_AFTER_IDLE, type = Count)
        verify(pixel, never()).fire(NTP_SHOWN_AFTER_IDLE_DAILY, type = Daily())
    }

    @Test
    fun whenIdleReturnTriggeredAndThenOnNtpShownThenFiresAfterIdleShownPixels() {
        testee.onIdleReturnTriggered()
        testee.onNtpShown()

        verify(pixel).fire(NTP_SHOWN_AFTER_IDLE, type = Count)
        verify(pixel).fire(NTP_SHOWN_AFTER_IDLE_DAILY, type = Daily())
        verify(pixel, never()).fire(NTP_SHOWN_USER_INITIATED, type = Count)
        verify(pixel, never()).fire(NTP_SHOWN_USER_INITIATED_DAILY, type = Daily())
    }

    @Test
    fun whenIdleReturnTriggeredAndOnNtpShownTwiceThenSecondCallIsUserInitiated() {
        testee.onIdleReturnTriggered()
        testee.onNtpShown() // consumes pending
        testee.onNtpShown() // no new trigger — classified as user-initiated

        verify(pixel).fire(NTP_SHOWN_AFTER_IDLE, type = Count)
        verify(pixel).fire(NTP_SHOWN_AFTER_IDLE_DAILY, type = Daily())
        verify(pixel).fire(NTP_SHOWN_USER_INITIATED, type = Count)
        verify(pixel).fire(NTP_SHOWN_USER_INITIATED_DAILY, type = Daily())
    }

    // --- onReturnToPageTapped classification ---

    @Test
    fun whenOnReturnToPageTappedWithoutAfterIdleContextThenHatchFiredAsUserInitiated() {
        testee.onNtpShown()

        testee.onReturnToPageTapped()

        verify(hatchPixels).fireReturnToPageTapped(afterIdle = false)
    }

    @Test
    fun whenOnReturnToPageTappedAfterIdleTriggeredNtpThenHatchFiredAsAfterIdle() {
        testee.onIdleReturnTriggered()
        testee.onNtpShown()

        testee.onReturnToPageTapped()

        verify(hatchPixels).fireReturnToPageTapped(afterIdle = true)
    }

    @Test
    fun whenUserInitiatedNtpShownAfterIdleNtpThenHatchFiredAsUserInitiated() {
        testee.onIdleReturnTriggered()
        testee.onNtpShown() // idle-triggered
        testee.onNtpShown() // user-initiated, resets classification

        testee.onReturnToPageTapped()

        verify(hatchPixels).fireReturnToPageTapped(afterIdle = false)
    }

    // --- onClose resets transient state across sessions ---

    @Test
    fun whenOnCloseThenPendingAfterIdleIsCleared() {
        testee.onIdleReturnTriggered()

        testee.onClose()
        testee.onNtpShown()

        verify(pixel).fire(NTP_SHOWN_USER_INITIATED, type = Count)
        verify(pixel).fire(NTP_SHOWN_USER_INITIATED_DAILY, type = Daily())
    }

    @Test
    fun whenOnCloseThenCurrentAfterIdleIsCleared() {
        testee.onIdleReturnTriggered()
        testee.onNtpShown() // currentAfterIdle = true

        testee.onClose()
        testee.onReturnToPageTapped()

        verify(hatchPixels).fireReturnToPageTapped(afterIdle = false)
    }

    @Test
    fun whenOnOpenThenPendingAfterIdleIsPreserved() {
        // onOpen must not clear pendingAfterIdle: FirstScreenHandlerImpl.onOpen may have
        // already called onIdleReturnTriggered synchronously and the multibinding order of
        // BrowserLifecycleObservers is not guaranteed.
        testee.onIdleReturnTriggered()

        testee.onOpen(isFreshLaunch = false)
        testee.onNtpShown()

        verify(pixel).fire(NTP_SHOWN_AFTER_IDLE, type = Count)
        verify(pixel).fire(NTP_SHOWN_AFTER_IDLE_DAILY, type = Daily())
    }

    @Test
    fun whenOnOpenThenCurrentAfterIdleIsCleared() {
        testee.onIdleReturnTriggered()
        testee.onNtpShown() // currentAfterIdle = true

        testee.onOpen(isFreshLaunch = false)
        testee.onReturnToPageTapped()

        verify(hatchPixels).fireReturnToPageTapped(afterIdle = false)
    }

    // --- onNtpSearchSubmitted classification ---

    @Test
    fun whenOnNtpSearchSubmittedWithoutAfterIdleContextThenUserInitiatedBarPixelsFired() {
        testee.onNtpShown()

        testee.onNtpSearchSubmitted()

        verify(pixel).fire(BAR_USED_FROM_NTP_USER_INITIATED, type = Count)
        verify(pixel).fire(BAR_USED_FROM_NTP_USER_INITIATED_DAILY, type = Daily())
        verify(pixel, never()).fire(BAR_USED_FROM_NTP_AFTER_IDLE, type = Count)
        verify(pixel, never()).fire(BAR_USED_FROM_NTP_AFTER_IDLE_DAILY, type = Daily())
    }

    @Test
    fun whenOnNtpSearchSubmittedAfterIdleTriggeredNtpThenAfterIdleBarPixelsFired() {
        testee.onIdleReturnTriggered()
        testee.onNtpShown()

        testee.onNtpSearchSubmitted()

        verify(pixel).fire(BAR_USED_FROM_NTP_AFTER_IDLE, type = Count)
        verify(pixel).fire(BAR_USED_FROM_NTP_AFTER_IDLE_DAILY, type = Daily())
        verify(pixel, never()).fire(BAR_USED_FROM_NTP_USER_INITIATED, type = Count)
        verify(pixel, never()).fire(BAR_USED_FROM_NTP_USER_INITIATED_DAILY, type = Daily())
    }

    // --- onIdleTimeoutSelected ---

    @Test
    fun whenOnIdleTimeoutSelectedWithKnownValueThenFiresMatchingPixelPair() {
        testee.onIdleTimeoutSelected(60L)

        verify(pixel).fire(TIMEOUT_SELECTED_60, type = Count)
        verify(pixel).fire(TIMEOUT_SELECTED_60_DAILY, type = Daily())
    }

    @Test
    fun whenOnIdleTimeoutSelectedWithAlwaysValueThenFiresAlwaysPixelPair() {
        testee.onIdleTimeoutSelected(0L)

        verify(pixel).fire(TIMEOUT_SELECTED_ALWAYS, type = Count)
        verify(pixel).fire(TIMEOUT_SELECTED_ALWAYS_DAILY, type = Daily())
    }

    @Test
    fun whenOnIdleTimeoutSelectedWithUnknownValueThenNoPixelFired() {
        testee.onIdleTimeoutSelected(42L)

        verifyNoInteractions(pixel)
    }
}
