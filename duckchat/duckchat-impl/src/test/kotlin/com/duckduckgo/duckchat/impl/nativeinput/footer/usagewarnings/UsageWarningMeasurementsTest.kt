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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings

import com.duckduckgo.duckchat.impl.nativeinput.footer.FakeNativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageWindow
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class UsageWarningMeasurementsTest {

    private val sender = FakeUsageWarningPixelSender()
    private val testee = UsageWarningMeasurements(sender)

    @Test
    fun whenTwoPluginsAskForTheSameHostThenTheyShareOneMeasurement() {
        val host = FakeNativeInputFooterHost()

        val first = testee.forHost(host) { DuckChatPixelSurface.DUCK_AI }
        val second = testee.forHost(host) { DuckChatPixelSurface.DUCK_AI }

        assertSame(first, second)
        assertNotSame(first, testee.forHost(FakeNativeInputFooterHost()) { DuckChatPixelSurface.DUCK_AI })
    }

    @Test
    fun whenASecondCardTakesTheSlotThenTheFirstCardsExposureEndsAndAPromptCountsOnce() {
        val host = FakeNativeInputFooterHost()
        val notice = UsageWarningExposure(UsageWarningExposureKind.HIGH_USAGE_MODEL_NOTICE, modelId = "claude-opus-4-8")
        val usage = UsageWarningExposure(UsageWarningExposureKind.APPROACHING, UsageWindow.WEEKLY, percentBucket = 50)

        testee.forHost(host) { DuckChatPixelSurface.DUCK_AI }.cardBecameVisible(notice)
        testee.forHost(host) { DuckChatPixelSurface.DUCK_AI }.cardBecameVisible(usage)
        testee.forHost(host) { DuckChatPixelSurface.DUCK_AI }.promptSubmitted()
        testee.forHost(host) { DuckChatPixelSurface.DUCK_AI }.promptSubmitted()

        assertEquals(
            listOf(
                UsageWarningEvent.Shown(notice),
                UsageWarningEvent.Abandoned(notice),
                UsageWarningEvent.Shown(usage),
                UsageWarningEvent.PromptSubmitted(usage),
            ),
            sender.events,
        )
    }
}
