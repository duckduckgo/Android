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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.res.use
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewOnboardingDialogTitleBinding

/**
 * The title of an onboarding dialog card: it types the title in, and reserves the space the finished title
 * will take so the card doesn't resize while it types. See [OnboardingDialogTitleController].
 *
 * Accepts `android:text` for the initial title.
 */
class OnboardingDialogTitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewOnboardingDialogTitleBinding =
        ViewOnboardingDialogTitleBinding.inflate(LayoutInflater.from(context), this)

    private val controller = OnboardingDialogTitleController(
        animatedText = binding.onboardingDialogTitleAnimatedText,
        sizingText = binding.onboardingDialogTitleSizingText,
    )

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.OnboardingDialogTitleView, 0, 0).use { attributes ->
            attributes.getString(R.styleable.OnboardingDialogTitleView_android_text)?.let(::setTitle)
        }
    }

    fun setTitle(text: String) = controller.setTitle(text)

    fun typeTitle(onFinished: () -> Unit = {}) = controller.typeTitle(onFinished)

    fun snapTitle() = controller.snapTitle()

    fun finishTyping() = controller.finishTyping()

    fun cancelAnimation() = controller.cancelAnimation()
}
