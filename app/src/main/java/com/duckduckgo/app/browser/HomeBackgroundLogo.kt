/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.AccelerateInterpolator
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import logcat.LogPriority.VERBOSE
import logcat.logcat

class HomeBackgroundLogo(private var ddgLogoView: View) {

    private var readyToShowLogo = false

    fun showLogo(omnibarPosition: OmnibarPosition) {
        updateLogoConstraint(omnibarPosition)
        this.readyToShowLogo = true
        update()
    }

    fun hideLogo() {
        readyToShowLogo = false
        update()
    }

    private fun update() {
        if (readyToShowLogo) {
            fadeLogoIn()
        } else {
            fadeLogoOut()
        }
    }

    private fun fadeLogoIn() {
        logcat(VERBOSE) { "showLogo" }
        // To avoid glitches when calling show/hide logo within a small amount of time we keep this 50ms animation
        ddgLogoView.animate().apply {
            duration = FADE_IN_DURATION
            interpolator = AccelerateInterpolator()
            alpha(1f)
            ddgLogoView.show()
        }
    }

    private fun fadeLogoOut() {
        logcat(VERBOSE) { "hideLogo" }
        ddgLogoView.gone()
    }

    private fun updateLogoConstraint(omnibarPosition: OmnibarPosition) {
        val params = ddgLogoView.layoutParams
        if (params is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
            when (omnibarPosition) {
                OmnibarPosition.TOP -> {
                    params.topToTop = R.id.guidelineTop
                    params.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                }
                OmnibarPosition.BOTTOM -> {
                    params.topToTop = R.id.guidelineBottom
                    params.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                }
            }
            ddgLogoView.layoutParams = params
        }
    }

    companion object {
        private const val FADE_IN_DURATION = 50L
    }
}
