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

package com.duckduckgo.app.browser.longpress

import android.webkit.WebView.HitTestResult.IMAGE_TYPE
import android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE
import android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE

enum class LongPressMenuShape { LINK, IMAGE, IMAGE_LINK }

data class LongPressMenuConfig(
    val shape: LongPressMenuShape,
    val primaryRowIsFireTab: Boolean,
    val showDedicatedFireTabRow: Boolean,
)

/**
 * Decides which branded long-press popup to show for a given hit-test type and browser mode.
 * Returns null for unsupported hit-test types (caller falls back to the native context menu).
 */
fun longPressMenuConfigFor(
    hitTestType: Int,
    isFireMode: Boolean,
): LongPressMenuConfig? {
    val shape = when (hitTestType) {
        SRC_ANCHOR_TYPE -> LongPressMenuShape.LINK
        IMAGE_TYPE -> LongPressMenuShape.IMAGE
        SRC_IMAGE_ANCHOR_TYPE -> LongPressMenuShape.IMAGE_LINK
        else -> return null
    }
    val hasLinkActions = shape != LongPressMenuShape.IMAGE
    return LongPressMenuConfig(
        shape = shape,
        primaryRowIsFireTab = hasLinkActions && isFireMode,
        showDedicatedFireTabRow = hasLinkActions && !isFireMode,
    )
}
