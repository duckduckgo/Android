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
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogTitleController
import com.duckduckgo.app.onboarding.ui.page.configdriven.InputScreenContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import com.duckduckgo.app.onboardingquicksetup.ui.BrandDesignInputScreenPicker.Transition
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stateful. Ported from BrandDesignUpdateWelcomePage:
 *  - animated path :1373, :1394-1420 (toggle snap-paint before the ChangeBounds morph, then
 *    startOnboardingTypingAnimation -> fade-in AnimatorSet -> startWithAiAnimation(delayedStart = true))
 *  - snap path :2971-2984 (updateAiChatToggleState, Transition.ANIMATE branch)
 *  - description text :1443-1444 (preventWidows then html())
 *
 * State-down-events-up: the picker's click listener is the only write path into [InputScreenContentState].
 * The initial `setSelection(Transition.NONE)` mirrors legacy's fresh paint (no crossfade, no Lottie yet — the
 * Lottie only starts once fade-in completes, via [ContentHandle.entrance]); state-down uses
 * `Transition.CROSSFADE_ANIMATE` so a later user toggle (or a value replayed on rebind) crossfades exactly like
 * legacy's `updateAiChatToggleState(transition = ANIMATE)` snap-path call.
 *
 * [BrandDesignInputScreenPicker.startWithAiAnimation] isn't Animator-based (it's a fire-and-forget coroutine
 * launched off the view's own lifecycleScope, see :129-131,152-180 there) — there is nothing to hand the engine
 * as the entrance animator itself. Instead we wrap it exactly like [ComparisonChartBinder]'s `avdStartTrigger`:
 * a zero-duration [ValueAnimator] whose only job is to invoke `startWithAiAnimation` from `onAnimationStart`, so
 * the engine still owns start()/end()/cancel() of *something* even though the real animation runs independently
 * of it. Documented POC simplification: because the Lottie loop has no meaningful "final frame" the way the
 * comparison-chart check icons do, there's no onAnimationEnd fallback here — if the engine ends this trigger
 * before ever starting it, the decorative loop simply never begins, which is the same as legacy's "no time to
 * finish typing before the user CTAs away" case (unbind's [BrandDesignInputScreenPicker.cancelLottieAnimations]
 * always cleans it up regardless).
 */
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
            // drop(1): collect() replays the current value immediately, and crossfadeLottie() unconditionally starts Lottie playback, so
            // without dropping the replay this would crossfade-and-start the loop at bind, defeating the entrance-gated withAiAnimationTrigger().
            state.drop(1).collect { inputScreenPicker.setSelection(it.withAi, Transition.CROSSFADE_ANIMATE) }
        }

        inputScreenDescription.text = content.description.resolve(context).preventWidows().html(context)

        val title = DialogTitleController(inputScreenTitle, inputScreenTitleHidden)
        title.set(content.title.resolve(context))

        ContentHandle(
            title = title,
            fadeTargets = listOf(inputScreenPicker, inputScreenDescription),
            entrance = { afterFade { withAiAnimationTrigger() } },
            result = { NewUserOnboardingEvent.InputModeConfirmed(state.value.withAi) },
            unbind = { inputScreenPicker.cancelLottieAnimations() },
        )
    }

    /**
     * Zero-duration animator used purely as a trigger for [BrandDesignInputScreenPicker.startWithAiAnimation].
     *
     * Caveat: "end()/cancel() before start means the animation never begins" only holds for `cancel()`.
     * `ValueAnimator.end()` on a never-started animator fires `onAnimationStart` synchronously first, so the
     * engine must `cancel()` — not `end()` — an unstarted entrance animator to suppress this trigger.
     */
    private fun withAiAnimationTrigger(): Animator =
        ValueAnimator.ofInt(0, 1).apply {
            duration = 0L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    binding.inputScreenPicker.startWithAiAnimation(delayedStart = true)
                }
            })
        }
}
