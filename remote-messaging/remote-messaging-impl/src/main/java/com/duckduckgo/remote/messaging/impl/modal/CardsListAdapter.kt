/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.modal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.impl.databinding.ViewRemoteMessageEntryBinding
import com.duckduckgo.remote.messaging.impl.mappers.drawable
import com.duckduckgo.remote.messaging.impl.modal.CardsListAdapter.CardItemViewHolder
import javax.inject.Inject

class CardsListAdapter @Inject constructor() : ListAdapter<CardItem, CardItemViewHolder>(ModalSurfaceDiffCallback()) {

    private lateinit var cardItemClickListener: CardItemClickListener

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CardItemViewHolder {
        return CardItemViewHolder(ViewRemoteMessageEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false), cardItemClickListener)
    }

    override fun onBindViewHolder(
        holder: CardItemViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position) as CardItem)
    }

    class CardItemViewHolder(
        private val binding: ViewRemoteMessageEntryBinding,
        private val listener: CardItemClickListener,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CardItem) {
            binding.title.text = item.titleText
            binding.description.text = item.descriptionText
            binding.startImage.setImageResource(item.placeholder.drawable(true))
            binding.root.setOnClickListener {
                listener.onItemClicked(item)
            }
        }
    }

    fun setListener(listener: CardItemClickListener) {
        this.cardItemClickListener = listener
    }

    private class ModalSurfaceDiffCallback : DiffUtil.ItemCallback<CardItem>() {
        override fun areItemsTheSame(
            oldItem: CardItem,
            newItem: CardItem,
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: CardItem,
            newItem: CardItem,
        ): Boolean {
            return oldItem == newItem
        }
    }
}
