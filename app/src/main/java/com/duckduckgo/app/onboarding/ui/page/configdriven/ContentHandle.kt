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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.animation.Animator
import android.view.View
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.view.OnboardingDialogTitleView
import kotlinx.coroutines.flow.Flow

/** What a binder hands back to the render engine after binding a screen. */
class ContentHandle(
    val title: OnboardingDialogTitleView?,
    /**
     * Faded in before [title] types.
     */
    val preTitleFadeTargets: List<View> = emptyList(),
    val fadeTargets: List<View>,
    /**
     * Bounded entrance animation, played once [fadeTargets] have faded in. A factory, not a running animator:
     * the engine decides when to start it, ends it when the render is snapped, and cancels it on teardown. An
     * animator it returns must leave its views in their final visible state even if `end()` arrives before it
     * ever ran.
     *
     * The card stops intercepting touches as this starts, so anything interactive revealed here is tappable
     * while still invisible. Gate it: `isClickable = false` at bind, restored from the animator's end listener.
     */
    val afterFade: (() -> Animator)? = null,
    /**
     * Side effect run at the same point as [afterFade], for entrance work the engine cannot own as an
     * [Animator]: an unbounded loop, or an animation driven outside the animator framework. Runs exactly once
     * per render, whether the entrance animated, snapped or was skipped, and never once the handle is unbound.
     *
     * The engine keeps no reference to whatever this starts, so [unbind] has to stop it. The input caveat on
     * [afterFade] applies here too.
     */
    val onContentReady: (() -> Unit)? = null,
    /**
     * Null means the primary CTA is always enabled. When set, the engine collects it for the lifetime of the
     * binding and applies it to the primary CTA button.
     */
    val primaryCtaState: CtaState? = null,
    /**
     * Called when the CTA button is tapped, if it was configured with [CtaAction.Submit], to build the
     * resulting event from dialog's live data. Never called for [CtaAction.Emit].
     */
    val result: (() -> NewUserOnboardingEvent)? = null,
    val unbind: () -> Unit = {},
)

class CtaState(
    val enabled: Flow<Boolean>,
    /** Gates the button until [enabled] emits its first value. */
    val defaultValue: Boolean,
)
