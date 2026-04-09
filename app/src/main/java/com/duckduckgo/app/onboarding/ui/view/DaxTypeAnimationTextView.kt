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

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.mobile.android.R
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.BreakIterator
import java.text.StringCharacterIterator
import kotlin.coroutines.CoroutineContext
import androidx.core.content.withStyledAttributes

@Suppress("NoHardcodedCoroutineDispatcher")
class DaxTypeAnimationTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialTextView(context, attrs, defStyleAttr), CoroutineScope {

    init {
        context.withStyledAttributes(attrs, R.styleable.DaxTextView, 0, 0) {
            val typography = if (hasValue(R.styleable.DaxTextView_typography)) {
                Typography.from(getInt(R.styleable.DaxTextView_typography, 0))
            } else {
                Typography.Body1
            }
            setTextAppearance(Typography.getTextAppearanceStyle(typography))

            if (!hasValue(R.styleable.DaxTextView_android_textColor)) {
                setTextColor(ContextCompat.getColorStateList(context, R.color.primary_text_color_selector))
            }
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + Job()

    private var typingAnimationJob: Job? = null
    var delayAfterAnimationInMs: Long = 300

    var typingDelayInMs: Long = 20
    private var completeText: Spanned? = null
    private var shouldRestartAnimation: Boolean = false
    private var afterAnimation: () -> Unit = {}

    fun startTypingAnimation(
        htmlText: String,
        isCancellable: Boolean = true,
        afterAnimation: () -> Unit = {},
    ) {
        completeText = htmlText.html(context)
        this.afterAnimation = afterAnimation

        if (isCancellable) {
            setOnClickListener {
                if (hasAnimationStarted()) {
                    finishAnimation()
                    afterAnimation()
                }
            }
        }

        typingAnimationJob?.cancel()

        typingAnimationJob = launch {
            animateTyping()
        }
    }

    private suspend fun animateTyping() {
        if (completeText != null) {
            val transparentSpan = ForegroundColorSpan(Color.TRANSPARENT)
            val partialText = SpannableString(completeText)
            breakSequence(partialText).forEach { index ->
                text = partialText.apply { setSpan(transparentSpan, index, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE) }
                delay(typingDelayInMs)
            }

            delay(delayAfterAnimationInMs)
            afterAnimation()
        }
    }

    private fun breakSequence(charSequence: CharSequence) =
        BreakIterator.getCharacterInstance()
            .apply { text = StringCharacterIterator(charSequence.toString()) }
            .let { generateSequence { it.next() } }
            .takeWhile { it != BreakIterator.DONE }

    fun hasAnimationStarted() = typingAnimationJob?.isActive == true

    fun hasAnimationFinished() = typingAnimationJob?.isCompleted == true

    fun finishAnimation() {
        cancelAnimation()
        completeText?.let { text = it }
    }

    fun cancelAnimation() = typingAnimationJob?.cancel()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (shouldRestartAnimation) {
            typingAnimationJob = launch {
                animateTyping()
            }
        }
    }

    override fun onDetachedFromWindow() {
        shouldRestartAnimation = hasAnimationStarted() && !hasAnimationFinished()
        cancelAnimation()

        super.onDetachedFromWindow()
    }
}
