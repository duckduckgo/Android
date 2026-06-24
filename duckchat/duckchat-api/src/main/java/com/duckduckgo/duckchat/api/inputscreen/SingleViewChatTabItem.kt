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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Convenience [NativeInputChatTabItem] for the common case of a single, static view (e.g. a card).
 *
 * Provide the view in [onCreateView] and a [visible] flow that drives whether the row is shown. The
 * flow is the single knob: compose it from whatever state the item cares about — e.g.
 * `inputQuery.map { it.isEmpty() }` for a zero-state card that hides while the user types, the item's
 * own data for a state-driven card, both combined, or `flowOf(true)` to always show. [hide] is a
 * sticky override (e.g. on dismiss) that wins over [visible] for the rest of the presentation.
 *
 * Items that need multiple rows, view recycling across many items, or several sections should
 * implement [NativeInputChatTabItem] directly.
 */
abstract class SingleViewChatTabItem(
    visible: Flow<Boolean>,
    scope: CoroutineScope,
) : NativeInputChatTabItem {

    /** Create the view shown as the single row. Called once; [parent] supplies the context. */
    abstract fun onCreateView(parent: ViewGroup): View

    private val singleViewAdapter = SingleViewAdapter()
    private val dismissed = MutableStateFlow(false)

    final override val adapters: List<RecyclerView.Adapter<*>> = listOf(singleViewAdapter)

    init {
        scope.launch {
            combine(visible, dismissed) { show, gone -> show && !gone }
                .collect { singleViewAdapter.setVisible(it) }
        }
    }

    /** Permanently hide this item for the current presentation (e.g. on dismiss); wins over [visible]. */
    protected fun hide() {
        dismissed.value = true
    }

    private inner class SingleViewAdapter : RecyclerView.Adapter<SingleViewHolder>() {
        private var visible = false

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
