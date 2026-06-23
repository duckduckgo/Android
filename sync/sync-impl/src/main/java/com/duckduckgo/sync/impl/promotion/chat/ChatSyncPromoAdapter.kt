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

package com.duckduckgo.sync.impl.promotion.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.sync.impl.R.drawable
import com.duckduckgo.sync.impl.R.string
import com.duckduckgo.sync.impl.databinding.ItemChatSyncPromoBinding

internal class ChatSyncPromoAdapter(
    private val listener: Listener,
) : Adapter<ChatSyncPromoViewHolder>() {
    interface Listener {
        fun onSyncWithDeviceClicked(adapter: ChatSyncPromoAdapter)

        fun onDismissClicked(adapter: ChatSyncPromoAdapter)

        fun onBannerShown(adapter: ChatSyncPromoAdapter)
    }

    private var isVisible = false

    fun show() {
        if (!isVisible) {
            isVisible = true
            notifyItemInserted(0)
        }
    }

    fun dismiss() {
        if (isVisible) {
            isVisible = false
            notifyItemRemoved(0)
        }
    }

    override fun getItemCount() = if (isVisible) 1 else 0

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ChatSyncPromoViewHolder {
        val binding = ItemChatSyncPromoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatSyncPromoViewHolder(
            binding = binding,
            onSyncClicked = { listener.onSyncWithDeviceClicked(this) },
            onDismissClicked = { listener.onDismissClicked(this) },
            onShown = { listener.onBannerShown(this) },
        )
    }

    override fun onBindViewHolder(
        holder: ChatSyncPromoViewHolder,
        position: Int,
    ) {
        holder.show()
    }
}

internal class ChatSyncPromoViewHolder(
    private val binding: ItemChatSyncPromoBinding,
    private val onSyncClicked: () -> Unit,
    private val onDismissClicked: () -> Unit,
    private val onShown: () -> Unit,
) : ViewHolder(binding.root) {
    init {
        binding.syncPromotion.apply {
            setMessage(
                MessageCta.Message(
                    topIllustration = drawable.ic_chat_sync_72,
                    title = context.getString(string.sync_chat_promo_banner_title),
                    action = context.getString(string.sync_chat_promo_banner_cta_title),
                ),
            )
            onPrimaryActionClicked(onSyncClicked)
            onCloseButtonClicked(onDismissClicked)
        }
    }

    fun show() {
        binding.root.doOnPreDraw { onShown() }
    }
}
