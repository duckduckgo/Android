/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.widget

import android.content.Context
import com.duckduckgo.common.ui.view.toDp
import logcat.LogPriority.INFO
import logcat.logcat
import com.duckduckgo.mobile.android.R as CommonR

class SearchAndFavoritesGridCalculator {
    fun calculateColumns(
        context: Context,
        width: Int,
    ): Int {
        val limitColumns = context.resources.getInteger(CommonR.integer.searchAndFavoritesWidgetGridColumns)
        val margins = context.resources.getDimension(CommonR.dimen.searchWidgetFavoriteMargin).toDp()
        val item = context.resources.getDimension(CommonR.dimen.searchWidgetFavoriteItemContainerWidth).toDp()
        val divider = context.resources.getDimension(CommonR.dimen.searchWidgetFavoritesHorizontalSpacing).toDp()

        // Buffer to prevent column count from changing with small size variations.
        val sizeBuffer = 8
        var n = 2
        var totalSize = (n * item) + ((n - 1) * divider) + (margins * 2)

        logcat(INFO) { "SearchAndFavoritesWidget width n:$n $totalSize vs $width" }
        while (totalSize + sizeBuffer <= width) {
            ++n
            totalSize = (n * item) + ((n - 1) * divider) + (margins * 2)
            logcat(INFO) { "SearchAndFavoritesWidget width n:$n $totalSize vs $width" }
        }

        return (n - 1).coerceIn(WIDGET_COLUMNS_MIN, limitColumns)
    }

    fun calculateRows(
        context: Context,
        height: Int,
    ): Int {
        val searchBar = context.resources.getDimension(CommonR.dimen.searchWidgetSearchBarHeight).toDp()
        val margins = context.resources.getDimension(CommonR.dimen.searchWidgetFavoritesTopMargin).toDp() +
            (context.resources.getDimension(CommonR.dimen.searchWidgetPadding).toDp() * 2)
        val item = context.resources.getDimension(CommonR.dimen.searchWidgetFavoriteItemContainerHeight).toDp()
        val divider = context.resources.getDimension(CommonR.dimen.searchWidgetFavoritesVerticalSpacing).toDp()

        // Buffer to prevent row count from changing with small size variations.
        val sizeBuffer = 8
        var n = 1
        var totalSize = searchBar + (n * item) + ((n - 1) * divider) + margins

        logcat(INFO) { "SearchAndFavoritesWidget height n:$n $totalSize vs $height" }
        while (totalSize + sizeBuffer <= height) {
            ++n
            totalSize = searchBar + (n * item) + ((n - 1) * divider) + margins
            logcat(INFO) { "SearchAndFavoritesWidget height n:$n $totalSize vs $height" }
        }

        return (n - 1).coerceIn(WIDGET_ROWS_MIN, WIDGET_ROWS_MAX)
    }

    companion object {
        private const val WIDGET_COLUMNS_MIN = 2
        private const val WIDGET_ROWS_MAX = 4
        private const val WIDGET_ROWS_MIN = 1
    }
}
