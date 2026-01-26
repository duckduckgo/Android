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

package com.duckduckgo.remote.messaging.impl.modal.cardslist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.impl.databinding.ViewRemoteMessageCardItemBinding
import com.duckduckgo.remote.messaging.impl.databinding.ViewRemoteMessageFeaturedListItemBinding
import com.duckduckgo.remote.messaging.impl.databinding.ViewRemoteMessageSectionTitleItemBinding
import com.duckduckgo.remote.messaging.impl.mappers.drawable
import javax.inject.Inject

class CardsListAdapter @Inject constructor() : ListAdapter<CardItem, CardsListAdapter.RemoteMessageItemHolder>(ModalSurfaceDiffCallback()) {

    private lateinit var cardItemClickListener: CardItemClickListener

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RemoteMessageItemHolder = when (viewType) {
        ITEM_TYPE_LIST_SECTION_TITLE -> RemoteMessageItemHolder.SectionTitleHolder(
            ViewRemoteMessageSectionTitleItemBinding.inflate(
                LayoutInflater.from(
                    parent.context,
                ),
                parent,
                false,
            ),
        )

        ITEM_TYPE_FEATURED_TWO_LINE_LIST_ITEM -> RemoteMessageItemHolder.FeaturedItemHolder(
            ViewRemoteMessageFeaturedListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            cardItemClickListener,
        )

        else -> RemoteMessageItemHolder.CardItemViewHolder(
            ViewRemoteMessageCardItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            cardItemClickListener,
        )
    }

    override fun onBindViewHolder(
        holder: RemoteMessageItemHolder,
        position: Int,
    ) {
        when (holder) {
            is RemoteMessageItemHolder.CardItemViewHolder -> {
                holder.bind(getItem(position) as CardItem.ListItem)
            }

            is RemoteMessageItemHolder.FeaturedItemHolder -> {
                holder.bind(getItem(position) as CardItem.ListItem)
            }

            is RemoteMessageItemHolder.SectionTitleHolder -> {
                holder.bind(getItem(position) as CardItem.SectionTitle)
            }
        }
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position).type) {
            CardItemType.TWO_LINE_LIST_ITEM -> ITEM_TYPE_TWO_LINE_LIST_ITEM
            CardItemType.LIST_SECTION_TITLE -> ITEM_TYPE_LIST_SECTION_TITLE
            CardItemType.FEATURED_TWO_LINE_SINGLE_ACTION_LIST_ITEM -> ITEM_TYPE_FEATURED_TWO_LINE_LIST_ITEM
        }

    sealed class RemoteMessageItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        class CardItemViewHolder(
            private val binding: ViewRemoteMessageCardItemBinding,
            private val listener: CardItemClickListener,
        ) : RemoteMessageItemHolder(binding.root) {

            fun bind(item: CardItem.ListItem) {
                binding.title.text = item.titleText
                binding.description.text = item.descriptionText
                binding.startImage.setImageResource(item.placeholder.drawable(true))
                binding.root.setOnClickListener {
                    listener.onItemClicked(item)
                }
            }
        }

        class SectionTitleHolder(
            private val binding: ViewRemoteMessageSectionTitleItemBinding,
        ) : RemoteMessageItemHolder(binding.root) {

            fun bind(item: CardItem.SectionTitle) {
                binding.sectionTitle.text = item.titleText
            }
        }

        class FeaturedItemHolder(
            private val binding: ViewRemoteMessageFeaturedListItemBinding,
            private val listener: CardItemClickListener,
        ) : RemoteMessageItemHolder(binding.root) {

            fun bind(item: CardItem.ListItem) {
                binding.title.text = item.titleText
                binding.description.text = item.descriptionText
                binding.image.setImageResource(item.placeholder.drawable(true))
                binding.action.text = item.primaryActionText
                binding.action.setOnClickListener {
                    listener.onItemClicked(item)
                }
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

    companion object {
        private const val ITEM_TYPE_FEATURED_TWO_LINE_LIST_ITEM = 0
        private const val ITEM_TYPE_TWO_LINE_LIST_ITEM = 1
        private const val ITEM_TYPE_LIST_SECTION_TITLE = 2
    }
}
