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

package com.duckduckgo.app.browser.contextmenu

import android.view.Gravity

data class PopupTouchPosition(
    val gravity: Int,
    val x: Int,
    val y: Int,
)

/**
 * Positions a popup near a touch point. The popup expands away from the nearer screen edge:
 * top-half touch opens downward, bottom-half upward; start-half opens toward start, end-half toward end.
 * x/y are returned as gravity-relative offsets (already mirrored for END/BOTTOM gravity).
 */
fun computePopupTouchPosition(
    touchX: Int,
    touchY: Int,
    screenWidth: Int,
    screenHeight: Int,
): PopupTouchPosition {
    val moreToStart = touchX < screenWidth / 2
    val moreToTop = touchY < screenHeight / 2
    val horizontalGravity = if (moreToStart) Gravity.START else Gravity.END
    val verticalGravity = if (moreToTop) Gravity.TOP else Gravity.BOTTOM
    val x = if (moreToStart) touchX else screenWidth - touchX
    val y = if (moreToTop) touchY else screenHeight - touchY
    return PopupTouchPosition(verticalGravity or horizontalGravity, x, y)
}
