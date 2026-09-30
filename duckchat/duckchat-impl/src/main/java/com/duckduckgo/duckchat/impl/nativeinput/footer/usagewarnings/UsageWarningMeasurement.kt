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

import androidx.annotation.MainThread
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNotice
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNoticeBand
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageWindow
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import dagger.SingleInstanceIn
import java.util.WeakHashMap
import javax.inject.Inject

enum class UsageWarningExposureKind { APPROACHING, LIMIT_REACHED, HIGH_USAGE_MODEL_NOTICE }

/** What a card was about, as the pixels report it. Two appearances of the same subject are one exposure. */
data class UsageWarningExposure(
    val kind: UsageWarningExposureKind,
    val window: UsageWindow? = null,
    val percentBucket: Int? = null,
    val modelId: String? = null,
) {
    companion object {
        fun of(notice: UsageNotice): UsageWarningExposure = if (notice.reached) {
            UsageWarningExposure(kind = UsageWarningExposureKind.LIMIT_REACHED, window = notice.window)
        } else {
            UsageWarningExposure(
                kind = UsageWarningExposureKind.APPROACHING,
                window = notice.window,
                percentBucket = UsageNoticeBand.of(notice.percentUsed).takeIf { it > 0 },
            )
        }

        fun ofHighUsageModel(modelId: String): UsageWarningExposure =
            UsageWarningExposure(kind = UsageWarningExposureKind.HIGH_USAGE_MODEL_NOTICE, modelId = modelId)
    }
}

enum class UsageWarningCta { SWITCH_MODEL, UPSELL, WEEKLY_LIMIT }

sealed class UsageWarningEvent {
    abstract val exposure: UsageWarningExposure

    data class Shown(override val exposure: UsageWarningExposure) : UsageWarningEvent()
    data class Dismissed(override val exposure: UsageWarningExposure) : UsageWarningEvent()
    data class CtaTapped(override val exposure: UsageWarningExposure, val cta: UsageWarningCta) : UsageWarningEvent()
    data class PromptSubmitted(override val exposure: UsageWarningExposure) : UsageWarningEvent()
    data class ModelSwitched(override val exposure: UsageWarningExposure) : UsageWarningEvent()
    data class Abandoned(override val exposure: UsageWarningExposure) : UsageWarningEvent()
}

interface UsageWarningPixelSender {
    fun send(
        event: UsageWarningEvent,
        surface: DuckChatPixelSurface,
    )
}

/**
 * One measurement per input widget, shared by every footer plugin. The footer slot shows one card at a
 * time, so a new card ends the previous card's exposure whichever plugin owns it, and a prompt is never
 * attributed to two cards. Keys are weak so a measurement goes away with its widget.
 */
@SingleInstanceIn(AppScope::class)
class UsageWarningMeasurements @Inject constructor(
    private val sender: UsageWarningPixelSender,
) {
    private val byHost = WeakHashMap<NativeInputFooterHost, UsageWarningMeasurement>()

    @MainThread
    fun forHost(
        host: NativeInputFooterHost,
        surface: () -> DuckChatPixelSurface,
    ): UsageWarningMeasurement = byHost.getOrPut(host) { UsageWarningMeasurement(sender, surface) }
}

class UsageWarningMeasurement(
    private val sender: UsageWarningPixelSender,
    private val surface: () -> DuckChatPixelSurface,
) {
    private class Exposure(val subject: UsageWarningExposure) {
        var didReportPrompt = false
        var didReportModelSwitch = false
        var didTapCta = false
        val hasFollowThrough: Boolean get() = didReportPrompt || didReportModelSwitch || didTapCta
    }

    private var exposure: Exposure? = null

    /** The same subject while an exposure is open is one appearance, so a re-render that only fills in the CTA does not count twice. */
    fun cardBecameVisible(subject: UsageWarningExposure) {
        if (exposure?.subject == subject) return
        endExposure()
        exposure = Exposure(subject)
        send(UsageWarningEvent.Shown(subject))
    }

    fun warningDismissed() {
        val exposure = exposure ?: return
        send(UsageWarningEvent.Dismissed(exposure.subject))
    }

    fun ctaTapped(cta: UsageWarningCta) {
        val exposure = exposure ?: return
        exposure.didTapCta = true
        send(UsageWarningEvent.CtaTapped(exposure.subject, cta))
    }

    fun promptSubmitted() {
        val exposure = exposure ?: return
        if (exposure.didReportPrompt) return
        exposure.didReportPrompt = true
        send(UsageWarningEvent.PromptSubmitted(exposure.subject))
    }

    /** A switch the user made themselves. The card's own switch CTA is already reported as a tap. */
    fun modelSwitched() {
        val exposure = exposure ?: return
        if (exposure.didReportModelSwitch || exposure.didTapCta) return
        exposure.didReportModelSwitch = true
        send(UsageWarningEvent.ModelSwitched(exposure.subject))
    }

    /** The input collapsing, leaving Duck.ai mode or going away: whatever the user was going to do about the message, they have done it. */
    fun inputSessionEnded() {
        endExposure()
    }

    private fun endExposure() {
        val exposure = exposure ?: return
        this.exposure = null
        if (exposure.hasFollowThrough) return
        send(UsageWarningEvent.Abandoned(exposure.subject))
    }

    private fun send(event: UsageWarningEvent) = sender.send(event, surface())
}
