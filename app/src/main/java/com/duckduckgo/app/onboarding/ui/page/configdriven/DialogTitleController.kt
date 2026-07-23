/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.widget.TextView
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows

/**
 * One owner for the copy-pasted title machinery: typing animation, invisible sizing twin,
 * preventWidows. POC stand-in for the OnboardingDialogTitleView compound widget.
 *
 * [text] is kept raw (post-preventWidows): [type] hands it to [TypeAnimationTextView.startTypingAnimation],
 * which html-decodes internally, so passing an already-decoded string here would double-decode. [set]'s
 * sizing twin and [snap]'s visible title are not routed through that method, so they go through [decoded]
 * instead, using the same `html` extension `TypeAnimationTextView` imports — otherwise a title containing a
 * literal tag (e.g. `<br/>`) would render un-decoded and mis-size the twin.
 */
class DialogTitleController(
    private val visibleTitle: TypeAnimationTextView,
    private val sizingTwin: TextView,
) {
    private var text: String = ""

    /** Stages the text: sizing twin reserves final height; visible title stays empty until type()/snap(). */
    fun set(text: String) {
        // A rebind while a previous typing animation is running would otherwise let the old coroutine repaint stale text and fire the old onFinished.
        visibleTitle.cancelAnimation()
        this.text = text.preventWidows()
        sizingTwin.text = decoded()
        visibleTitle.text = ""
    }

    fun type(onFinished: () -> Unit) {
        visibleTitle.delayAfterAnimationInMs = TYPING_DELAY_MS
        visibleTitle.typingDelayInMs = TYPING_DELAY_MS
        visibleTitle.startTypingAnimation(text, isCancellable = true, afterAnimation = onFinished)
    }

    /** Snap path: full text immediately, no animation. */
    fun snap() {
        visibleTitle.cancelAnimation()
        visibleTitle.text = decoded()
    }

    /** Html-decoded representation of [text], for consumers that don't go through [TypeAnimationTextView.startTypingAnimation]. */
    private fun decoded(): CharSequence = text.html(visibleTitle.context)

    /**
     * Tap-to-skip: finish a running typing animation.
     *
     * [TypeAnimationTextView.finishAnimation] only cancels the animation job and snaps the text —
     * it does NOT invoke the `afterAnimation` callback passed to [TypeAnimationTextView.startTypingAnimation].
     * The legacy tap-to-skip (`BrandDesignUpdateWelcomePage.skipCurrentDialogAnimation`) instead calls
     * `performClick()` on titles whose animation has started, which reuses the click listener that
     * `startTypingAnimation(isCancellable = true, ...)` already installed — that listener calls
     * `finishAnimation()` and then `afterAnimation()` in sequence. Mirror that exactly here so the
     * `onFinished` callback passed to [type] still fires.
     */
    fun finishTyping() {
        if (visibleTitle.hasAnimationStarted()) {
            visibleTitle.performClick()
        }
    }

    fun cancel() {
        visibleTitle.cancelAnimation()
    }

    private companion object {
        const val TYPING_DELAY_MS = 20L
    }
}
