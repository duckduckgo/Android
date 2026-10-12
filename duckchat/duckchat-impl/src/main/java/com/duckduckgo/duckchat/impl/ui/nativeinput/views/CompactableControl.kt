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
package com.duckduckgo.duckchat.impl.ui.nativeinput.views

/**
 * A bottom-row control whose width depends on what it shows, so the row can decide its layout from worst-case
 * widths and never reshuffle because of the selected model, the typed text or an active mode.
 * Levels are cumulative: [AdaptiveBottomRowLayout.LEVEL_FULL] is the normal look, and each higher level frees more width.
 */
interface CompactableControl {
    fun setCompactLevel(level: Int)

    /** The most width this control may need at [level], including its container's margin, whether or not it is showing. */
    fun worstCaseWidth(level: Int): Int
}
