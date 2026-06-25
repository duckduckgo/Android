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

package com.duckduckgo.common.ui.menu

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupTouchPositionTest {

    @Test
    fun topStartTouchOpensFromTopStartWithRawOffsets() {
        val p = computePopupTouchPosition(touchX = 100, touchY = 200, screenWidth = 1000, screenHeight = 2000)
        assertEquals(Gravity.TOP or Gravity.START, p.gravity)
        assertEquals(100, p.x)
        assertEquals(200, p.y)
    }

    @Test
    fun bottomEndTouchOpensFromBottomEndWithMirroredOffsets() {
        val p = computePopupTouchPosition(touchX = 900, touchY = 1800, screenWidth = 1000, screenHeight = 2000)
        assertEquals(Gravity.BOTTOM or Gravity.END, p.gravity)
        assertEquals(100, p.x) // 1000 - 900
        assertEquals(200, p.y) // 2000 - 1800
    }
}
