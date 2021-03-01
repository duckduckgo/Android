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
import android.view.animation.AccelerateInterpolator
import com.duckduckgo.app.global.view.hide
import com.duckduckgo.app.global.view.show
import timber.log.Timber

class HomeBackgroundLogo(private var ddgLogoView: View) {

    private var readyToShowLogo = false

    fun showLogo() {
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
        Timber.v("showLogo")
        ddgLogoView.animate().apply {
            duration = FADE_IN_DURATION
            interpolator = AccelerateInterpolator()
            alpha(1f)
            ddgLogoView.show()
        }
    }

    private fun fadeLogoOut() {
        Timber.v("hideLogo")
        ddgLogoView.hide()

    }

    companion object {
        private const val FADE_IN_DURATION = 50L
    }
}
