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
 * The title machinery behind [OnboardingDialogTitleView].
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

    fun finishTyping() {
        // TypeAnimationTextView.finishAnimation snaps the text but doesn't invoke that callback.
        // Workaround by using the click listener, which does both in order.
        if (animatedText.hasAnimationStarted()) {
            animatedText.performClick()
        }
    }

    fun cancelAnimation() {
        animatedText.cancelAnimation()
    }

    private fun decodedTitle(): CharSequence = title.html(animatedText.context)

    private companion object {
        const val TYPING_DELAY_MS = 20L
        const val TYPING_POST_DELAY_MS = 20L
    }
}
