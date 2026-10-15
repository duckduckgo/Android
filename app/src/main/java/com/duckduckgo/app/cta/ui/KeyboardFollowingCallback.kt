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

package com.duckduckgo.app.cta.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps bottom-anchored views riding the keyboard edge. The tab's IME inset is applied as a single layout step when
 * the keyboard starts moving, so without this those views jump to their end position while the keyboard is still
 * animating towards it. [layoutBottomInset] is the inset the tab is currently laid out against.
 */
internal class KeyboardFollowingCallback(
    private val views: () -> List<View>,
    private val layoutBottomInset: () -> Int,
    private val onStarted: () -> Unit,
    private val onEnded: () -> Unit,
) : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {

    private var runningImeAnimations = 0
    var isAnimating = false
        private set
    private var baseTranslations: Map<View, Float> = emptyMap()
    private var startBottom = 0

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        if (!animation.isIme()) return
        // A reversal can start a new animation before the previous one ends; keep the at-rest state from the first.
        if (runningImeAnimations++ == 0) {
            baseTranslations = views().associateWith { it.translationY }
            isAnimating = true
            onStarted()
        }
        startBottom = rootBottomInset()
    }

    override fun onStart(
        animation: WindowInsetsAnimationCompat,
        bounds: WindowInsetsAnimationCompat.BoundsCompat,
    ): WindowInsetsAnimationCompat.BoundsCompat {
        if (animation.isIme()) translate(layoutBottomInset() - startBottom)
        return bounds
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: MutableList<WindowInsetsAnimationCompat>,
    ): WindowInsetsCompat {
        if (runningAnimations.any { it.isIme() }) translate(layoutBottomInset() - insets.getInsets(INSET_TYPES).bottom)
        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        if (!animation.isIme() || runningImeAnimations == 0) return
        if (--runningImeAnimations == 0) restore()
    }

    // Also called when the callback is removed, since an animation interrupted by that never reaches onEnd.
    fun restore() {
        if (!isAnimating) return
        isAnimating = false
        runningImeAnimations = 0
        translate(0)
        baseTranslations = emptyMap()
        onEnded()
    }

    private fun translate(offsetPx: Int) {
        baseTranslations.forEach { (view, base) -> view.translationY = base + offsetPx }
    }

    private fun rootBottomInset(): Int =
        baseTranslations.keys.firstOrNull()
            ?.let { ViewCompat.getRootWindowInsets(it) }
            ?.getInsets(INSET_TYPES)
            ?.bottom ?: 0

    private fun WindowInsetsAnimationCompat.isIme(): Boolean = typeMask and WindowInsetsCompat.Type.ime() != 0

    private companion object {
        val INSET_TYPES = WindowInsetsCompat.Type.navigationBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime()
    }
}
