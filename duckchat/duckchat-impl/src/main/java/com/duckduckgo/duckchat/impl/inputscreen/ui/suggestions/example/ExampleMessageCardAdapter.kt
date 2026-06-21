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

package com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.example

import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.duckchat.impl.R

/**
 * Single-item adapter that renders an example [MessageCta] at the top of the Chat tab.
 *
 * Dismissing the card empties the adapter (itemCount 1 → 0). This is the interesting part of the
 * showcase: the item drives its own content, and because the host folds plugin `itemCount` into the
 * suggestions overlay's `hasContent`, an emptied card stops contributing to the overlay.
 */
internal class ExampleMessageCardAdapter(
    private val onPrimaryAction: () -> Unit,
) : RecyclerView.Adapter<ExampleMessageCardAdapter.ViewHolder>() {

    private var visible = true

    fun dismiss() {
        if (!visible) return
        visible = false
        notifyItemRemoved(0)
    }

    override fun getItemCount(): Int = if (visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = MessageCta(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            // The card lives in a RecyclerView; let the adapter own its state rather than the view tree.
            disableStateSaving()
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(onPrimaryAction = onPrimaryAction, onDismiss = ::dismiss)
    }

    class ViewHolder(private val card: MessageCta) : RecyclerView.ViewHolder(card) {
        fun bind(
            onPrimaryAction: () -> Unit,
            onDismiss: () -> Unit,
        ) {
            val context = card.context
            card.onCloseButtonClicked { onDismiss() }
            card.onPrimaryActionClicked { onPrimaryAction() }
            card.setMessage(
                MessageCta.Message(
                    title = context.getString(R.string.duckChatExampleMessageCardTitle),
                    subtitle = context.getString(R.string.duckChatExampleMessageCardSubtitle),
                    action = context.getString(R.string.duckChatExampleMessageCardAction),
                ),
            )
        }
    }
}
