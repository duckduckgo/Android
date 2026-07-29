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

package com.duckduckgo.app.onboarding.ui.view

import android.widget.TextView
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows

/**
 * The title machinery behind [OnboardingDialogTitleView], kept apart from the view so it can be exercised
 * without inflating anything: [animatedText] types the title in, while [sizingText] already holds the whole
 * title so the card reserves its final height instead of growing as the text types.
 */
internal class OnboardingDialogTitleController(
    private val animatedText: TypeAnimationTextView,
    private val sizingText: TextView,
) {

    private var title: String = ""

    /**
     * Stages [text] as the title: [sizingText] takes it immediately so the card can reserve its final
     * height, while [animatedText] stays empty until [typeTitle] or [snapTitle].
     */
    fun setTitle(text: String) {
        // Without this, a title set while a previous typing animation is still running would keep being
        // overpainted by that animation's remaining frames.
        animatedText.cancelAnimation()
        title = text.preventWidows()
        sizingText.text = decodedTitle()
        animatedText.text = ""
    }

    fun typeTitle(onFinished: () -> Unit = {}) {
        animatedText.typingDelayInMs = TYPING_DELAY_MS
        animatedText.delayAfterAnimationInMs = TYPING_POST_DELAY_MS
        animatedText.startTypingAnimation(title, isCancellable = true, afterAnimation = onFinished)
    }

    fun snapTitle() {
        animatedText.cancelAnimation()
        animatedText.text = decodedTitle()
    }

    /**
     * Tap-to-skip: completes a running typing animation, [typeTitle]'s `onFinished` included.
     *
     * [TypeAnimationTextView.finishAnimation] snaps the text but doesn't invoke that callback, so go through
     * the click listener [TypeAnimationTextView.startTypingAnimation] installs, which does both in order.
     */
    fun finishTyping() {
        if (animatedText.hasAnimationStarted()) {
            animatedText.performClick()
        }
    }

    fun cancelAnimation() {
        animatedText.cancelAnimation()
    }

    /**
     * [title] is kept as authored because [typeTitle] hands it to [TypeAnimationTextView.startTypingAnimation],
     * which decodes it itself. The paths that assign text directly have to decode here instead, or a title
     * carrying markup (`<br/>`) would render its tags literally and mis-measure [sizingText].
     */
    private fun decodedTitle(): CharSequence = title.html(animatedText.context)

    private companion object {
        const val TYPING_DELAY_MS = 20L
        const val TYPING_POST_DELAY_MS = 20L
    }
}
