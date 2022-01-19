/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.feedback.ui.common

import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView

class LayoutScrollingTouchListener(
    private val scrollView: ScrollView,
    private val desiredScrollPosition: Int
) : View.OnTouchListener {

    override fun onTouch(
        v: View?,
        event: MotionEvent?
    ): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            v?.performClick()
            scrollView.postDelayed({ scrollView.smoothScrollTo(0, desiredScrollPosition) }, POST_DELAY_MS)
        }
        return false
    }

    companion object {
        private const val POST_DELAY_MS = 300L
    }
}
