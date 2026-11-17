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

package com.duckduckgo.common.ui.compose.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One design system screenshot, rendered the same way by every screenshot testing library.
 *
 * [name] names the reference image, so it must be unique across the catalog.
 */
abstract class ScreenshotCase(
    val name: String,
    val frame: ScreenshotFrame = ScreenshotFrame.WrapContent,
) {
    @Composable
    abstract fun Content()

    override fun toString(): String = name
}

sealed interface ScreenshotFrame {
    /** The image is as big as the content. */
    data object WrapContent : ScreenshotFrame

    /** The content is laid out in a box of this size. */
    data class Fixed(val widthDp: Int, val heightDp: Int) : ScreenshotFrame

    /**
     * The whole screen is captured, so dialog and popup windows (bottom sheets, menus) show up too.
     * Libraries that can't capture other windows fall back to a box of this size.
     */
    data class Screen(val widthDp: Int, val heightDp: Int) : ScreenshotFrame
}

/** Draws [case] on a white background, sized by its [ScreenshotCase.frame]. */
@Composable
fun ScreenshotFrame(case: ScreenshotCase, modifier: Modifier = Modifier) {
    val sizeModifier = when (val frame = case.frame) {
        ScreenshotFrame.WrapContent -> Modifier
        is ScreenshotFrame.Fixed -> Modifier.size(frame.widthDp.dp, frame.heightDp.dp)
        is ScreenshotFrame.Screen -> Modifier.size(frame.widthDp.dp, frame.heightDp.dp)
    }
    Box(modifier = modifier.then(sizeModifier).background(Color.White)) {
        case.Content()
    }
}
