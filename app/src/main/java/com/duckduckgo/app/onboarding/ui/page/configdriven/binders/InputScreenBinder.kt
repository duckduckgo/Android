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

package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignInputScreenBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.InputScreenContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import com.duckduckgo.app.onboardingquicksetup.ui.BrandDesignInputScreenPicker.Transition
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InputScreenBinder(
    private val binding: IncludeBrandDesignInputScreenBinding,
    private val isLightMode: () -> Boolean,
) : StatefulDialogBinder<ContentConfig.InputScreen, InputScreenContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.InputScreen,
        state: MutableStateFlow<InputScreenContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        val context = root.context

        inputScreenPicker.setLightMode(isLightMode())
        inputScreenPicker.setSelection(state.value.withAi, Transition.NONE)
        inputScreenPicker.setOnSelectionChangedListener { withAi -> state.update { it.copy(withAi = withAi) } }
        scope.coroutineScope.launch {
            // The replayed first value would crossfade and start the Lottie loop at bind time, which the
            // entrance trigger below owns.
            state.drop(1).collect { inputScreenPicker.setSelection(it.withAi, Transition.CROSSFADE_ANIMATE) }
        }

        inputScreenDescription.text = content.description.resolve(context).preventWidows().html(context)

        inputScreenTitle.setTitle(content.title.resolve(context))

        ContentHandle(
            title = inputScreenTitle,
            fadeTargets = listOf(inputScreenPicker, inputScreenDescription),
            afterFade = { withAiFlourishTrigger() },
            result = { NewUserOnboardingEvent.InputModeConfirmed(state.value.withAi) },
            unbind = { inputScreenPicker.cancelLottieAnimations() },
        )
    }

    /**
     * The picker's flourish runs on its own coroutine, not on an animator timeline, so there is nothing to hand
     * the engine directly. This zero-duration animator exists only so the engine still owns starting it.
     */
    private fun withAiFlourishTrigger(): Animator =
        ValueAnimator.ofInt(0, 1).apply {
            duration = 0L
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        binding.inputScreenPicker.startWithAiAnimation(delayedStart = true)
                    }
                },
            )
        }
}
