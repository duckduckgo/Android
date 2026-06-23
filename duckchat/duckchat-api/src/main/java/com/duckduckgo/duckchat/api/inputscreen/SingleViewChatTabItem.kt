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

package com.duckduckgo.duckchat.api.inputscreen

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Convenience [NativeInputChatTabItem] for the common case of a single, static view (e.g. a card).
 *
 * Provide the view in [onCreateView]. By default the item is a zero-state element — visible only while
 * the query is empty and hidden as soon as the user types; override [shouldShow] to change that. Call
 * [hide] to remove it (e.g. when the user dismisses it).
 *
 * This spares simple contributors from writing a single-item `RecyclerView.Adapter`. Items that need
 * multiple rows, view recycling across many items, or several sections should implement
 * [NativeInputChatTabItem] directly.
 */
abstract class SingleViewChatTabItem : NativeInputChatTabItem {

    /** Create the view shown as the single row. Called once; [parent] supplies the context. */
    abstract fun onCreateView(parent: ViewGroup): View

    /** Whether the item should be visible for [query]. Default: visible only while the query is empty. */
    open fun shouldShow(query: String): Boolean = query.isEmpty()

    private val singleViewAdapter = SingleViewAdapter()
    private var dismissed = false

    final override val adapters: List<RecyclerView.Adapter<*>> = listOf(singleViewAdapter)

    final override fun onQueryChanged(query: String) {
        if (dismissed) return
        singleViewAdapter.setVisible(shouldShow(query))
    }

    /** Permanently hide this item for the current presentation (e.g. on dismiss); [shouldShow] no
     *  longer applies after this. */
    protected fun hide() {
        dismissed = true
        singleViewAdapter.setVisible(false)
    }

    private inner class SingleViewAdapter : RecyclerView.Adapter<SingleViewHolder>() {
        private var visible = true

        fun setVisible(value: Boolean) {
            if (value == visible) return
            visible = value
            if (value) notifyItemInserted(0) else notifyItemRemoved(0)
        }

        override fun getItemCount(): Int = if (visible) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SingleViewHolder {
            val view = onCreateView(parent).apply {
                if (layoutParams == null) {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
            }
            return SingleViewHolder(view)
        }

        override fun onBindViewHolder(holder: SingleViewHolder, position: Int) = Unit
    }

    private class SingleViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
