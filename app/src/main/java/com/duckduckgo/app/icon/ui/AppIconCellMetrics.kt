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

package com.duckduckgo.app.icon.ui

import android.content.res.Resources
import com.duckduckgo.app.browser.R
import kotlin.math.roundToInt
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Geometry of a cell in the icon grid. The selection outline is drawn inside the cell's own padding,
 * so [selectionInset] is the room the cell has to leave around its icon for the outline to land in,
 * and it comes off [cellGap] rather than being added to it. That leaves the spacing the design asks
 * for between the icons themselves rather than between the cells.
 */
class AppIconCellMetrics(resources: Resources) {

    val selectionStrokeWidth: Float = resources.getDimension(R.dimen.changeAppIconSelectionStrokeWidth)

    val selectionInset: Int = (selectionStrokeWidth + resources.getDimension(R.dimen.changeAppIconSelectionGap)).roundToInt()

    val cellSize: Int = resources.getDimensionPixelSize(CommonR.dimen.changeAppIconSize)

    private val iconSpacing: Int = resources.getDimensionPixelSize(R.dimen.changeAppIconSpacing)

    val cellGap: Int = (iconSpacing - 2 * selectionInset).coerceAtLeast(0)
}
