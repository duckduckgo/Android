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
import androidx.core.view.isVisible
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.show
import com.duckduckgo.app.global.view.toDp
import timber.log.Timber


class LogoHidingLayoutChangeListener(private val ddgLogoView: View, private val callToActionButton: View) : View.OnLayoutChangeListener {

    override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        update()
    }

    fun update() {
        val r = Rect()
        ddgLogoView.getWindowVisibleDisplayFrame(r)
        val heightDp = r.height().toDp()

        Timber.v("App height now: ${r.height()} px, $heightDp dp, call to action button showing: ${callToActionButton.isVisible}")

        if (enoughRoomForLogo(heightDp)) {
            ddgLogoView.show()
        } else {
            ddgLogoView.gone()
        }
    }

    private fun enoughRoomForLogo(heightDp: Int): Boolean {
        val minimumHeight =
            if (callToActionButton.isVisible) MINIMUM_HEIGHT_REQUIRED_CALL_TO_ACTION_SHOWING else MINIMUM_HEIGHT_REQUIRED_CALL_TO_ACTION_HIDDEN
        return heightDp >= minimumHeight
    }

    companion object {
        private const val MINIMUM_HEIGHT_REQUIRED_CALL_TO_ACTION_SHOWING = 230
        private const val MINIMUM_HEIGHT_REQUIRED_CALL_TO_ACTION_HIDDEN = 150
    }

}