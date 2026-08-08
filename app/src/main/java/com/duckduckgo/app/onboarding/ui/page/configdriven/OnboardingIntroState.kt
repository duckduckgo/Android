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

/**
 * Ordering rules behind [OnboardingIntroChoreographer]: what a call is allowed to do to the intro views given what
 * happened to them already.
 */
class OnboardingIntroState {

    private var visualsOnScreen = false

    /** The intro is out of play: faded out for a dialog, or snapped away without ever being shown. */
    private var cleared = false

    /** A dialog has taken the background over from the intro. */
    private var handedOver = false

    private var released = false

    fun play() {
        visualsOnScreen = true
    }

    /** @return true when should run, false when something overtook it */
    fun canStart(): Boolean = !released && !cleared

    /** @return true when the caller should snap the intro views to their end state, false when they are there already */
    fun restore(): Boolean {
        if (visualsOnScreen) return false
        visualsOnScreen = true
        return true
    }

    /** @return what an arriving dialog leaves the caller to do with the intro views */
    fun handOverToDialog(): Handover {
        if (handedOver) return Handover.AlreadyHandedOver
        handedOver = true
        if (cleared) return Handover.AlreadyDismissed
        cleared = true
        return if (visualsOnScreen) Handover.FadeOut else Handover.SnapAway
    }

    /** @return true when the caller should snap the intro away, false when it was shown or is already out of play */
    fun dismissUnplayed(): Boolean {
        if (visualsOnScreen || cleared) return false
        cleared = true
        return true
    }

    fun release() {
        released = true
    }

    enum class Handover {
        /** The intro visuals are on screen, fade them out. */
        FadeOut,

        /** This view never showed the intro, snap the views past it. */
        SnapAway,

        /** The intro was already snapped away unplayed. */
        AlreadyDismissed,

        /** An earlier dialog already took the background over. */
        AlreadyHandedOver,
    }
}
