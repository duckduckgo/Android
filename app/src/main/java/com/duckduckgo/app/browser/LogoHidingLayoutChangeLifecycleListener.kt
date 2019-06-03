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

import android.graphics.Rect
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.duckduckgo.app.global.view.toDp
import timber.log.Timber


class LogoHidingLayoutChangeLifecycleListener(private var ddgLogoView: View) : View.OnLayoutChangeListener {

    var callToActionView: View? = null

    private var readyToShowLogo = false

    fun onReadyToShowLogo() {
        readyToShowLogo = true
        update()
    }

    override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        update()
    }

    fun onResume() {
        update()
    }

    fun onPause() {
        hideLogo()
    }

    private fun update() {
        val heightDp = getHeightDp()
        Timber.v("App height now: $heightDp dp, call to action button showing: ${callToActionView?.isVisible}")
        if (readyToShowLogo && enoughRoomForLogo(heightDp)) {
            fadeLogoIn()
        } else {
            fadeLogoOut()
        }
    }

    private fun fadeLogoIn() {
        ddgLogoView.animate().apply {
            duration = FADE_IN_DURATION
            interpolator = AccelerateInterpolator()
            alpha(1f)
        }
    }

    private fun fadeLogoOut() {
        ddgLogoView.animate().apply {
            duration = FADE_OUT_DURATION
            interpolator = AccelerateInterpolator()
            alpha(0f)
        }
    }

    private fun hideLogo() {
        ddgLogoView.alpha = 0f
    }

    private fun getHeightDp(): Int {
        val r = Rect()
        ddgLogoView.getWindowVisibleDisplayFrame(r)
        return r.height().toDp()
    }

    private fun enoughRoomForLogo(heightDp: Int): Boolean {

        val isCallToActionHidden = callToActionView?.isGone ?: true
        if (isCallToActionHidden) {
            return true
        }
        val callToActionButtonHeightDp = callToActionView?.measuredHeight?.toDp() ?: 0
        val heightMinusCallToAction = heightDp - callToActionButtonHeightDp
        if (heightMinusCallToAction >= MINIMUM_AVAILABLE_HEIGHT_REQUIRED_TO_SHOW_LOGO) {
            return true
        }

        return false
    }

    companion object {
        private const val MINIMUM_AVAILABLE_HEIGHT_REQUIRED_TO_SHOW_LOGO = 220
        private const val FADE_IN_DURATION = 1000L
        private const val FADE_OUT_DURATION = 100L
    }

}