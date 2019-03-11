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

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.global.view.toPx
import timber.log.Timber

class FeedbackItemDecoration(private val divider: Drawable?) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {

//        val position = parent.getChildAdapterPosition(view)
//
//        Timber.i("Deciding item decoration for item at position $position, total items = ${parent.totalItemCount()}")
//        if (isFirstItem(position) || isLastItem(position, parent)) {
//            Timber.i("Dealing with first or last item")
//        } else {
//            Timber.i("Dealing with item in the list (not top nor bottom)")
//            outRect.left = 22.toPx()
//        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (divider == null) return

        val count = parent.childCount
        val width = parent.width


        for (i in 0 until count) {

            Timber.i("Deciding item decoration for item at position $i, total items = ${parent.totalItemCount()}")
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams

            val extraLeftSpace = if(shouldAddExtraPadding(i, parent)) 22.toPx() else 0

            val dividerTop = child.bottom + params.bottomMargin
            val dividerBottom = dividerTop + divider.intrinsicHeight
            val dividerLeft = parent.paddingLeft
            val dividerRight = width - parent.paddingRight

            divider.setBounds(dividerLeft + extraLeftSpace, dividerTop, dividerRight, dividerBottom)
            divider.draw(c)
        }
    }

    private fun shouldAddExtraPadding(position:Int, parent: RecyclerView) : Boolean = isFirstItem(position)|| isLastItem(position, parent)
    private fun isFirstItem(position: Int) = position == 0
    private fun isLastItem(position: Int, parent: RecyclerView) = (position + 1) == parent.totalItemCount()
    private fun RecyclerView.totalItemCount() = adapter?.itemCount
}
