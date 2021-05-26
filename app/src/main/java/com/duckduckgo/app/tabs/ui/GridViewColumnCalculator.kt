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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import com.duckduckgo.app.global.view.toDp
import com.duckduckgo.app.global.view.toPx
import kotlin.math.min

class GridViewColumnCalculator(val context: Context) {

    fun calculateNumberOfColumns(columnWidthDp: Int, maxColumns: Int): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels.toDp()
        val numberOfColumns = screenWidthDp / columnWidthDp
        return min(maxColumns, numberOfColumns)
    }

    /**
     * Given a numOfColumns and their width, calculate sides padding to center all items on the GridView.
     * RecyclerView should have a match_parent width to avoid clipping items if drag-drop enabled.
     *
     * @return start/end padding in pixels
     */
    fun calculateSidePadding(columnWidthDp: Int, numOfColumns: Int): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels.toDp()
        val columnsWidth = columnWidthDp * numOfColumns
        val remainingSpace = screenWidthDp - columnsWidth
        return if (remainingSpace <= 0) 0 else (remainingSpace / 2).toPx()
    }
}
