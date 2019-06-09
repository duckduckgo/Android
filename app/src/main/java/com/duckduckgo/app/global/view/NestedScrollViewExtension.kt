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

package com.duckduckgo.app.global.view

import androidx.core.view.get
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min

/**
 * Scroll to a [RecyclerView] item position if the recycler view is nested in a [NestedScrollView]
 * @param position Int of the recycler view item index
 * @param recyclerView RecyclerView inside this nested scroll view
 */
fun NestedScrollView.smoothScrollTo(position: Int, recyclerView: RecyclerView) {
    post {
        if (recyclerView.childCount > 0) {
            val currentItemY = recyclerView[min(position, recyclerView.childCount - 1)].y.toInt()
            smoothScrollTo(0, currentItemY)
        }
    }
}