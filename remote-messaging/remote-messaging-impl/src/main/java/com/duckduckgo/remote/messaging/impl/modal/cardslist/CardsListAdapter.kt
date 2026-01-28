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

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.impl.databinding.ViewRemoteMessageCardItemBinding
import com.duckduckgo.remote.messaging.impl.databinding.ViewRemoteMessageFeaturedListItemBinding
import com.duckduckgo.remote.messaging.impl.databinding.ViewRemoteMessageHeaderBinding
import com.duckduckgo.remote.messaging.impl.databinding.ViewRemoteMessageSectionTitleItemBinding
import com.duckduckgo.remote.messaging.impl.mappers.drawable
import java.io.File
import javax.inject.Inject

class CardsListAdapter @Inject constructor() : ListAdapter<ModalListItem, CardsListAdapter.RemoteMessageItemHolder>(ModalSurfaceDiffCallback()) {

    private lateinit var cardItemClickListener: CardItemClickListener
    var headerImageLoadListener: HeaderImageLoadListener? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RemoteMessageItemHolder = when (viewType) {
        ITEM_TYPE_HEADER -> RemoteMessageItemHolder.HeaderViewHolder(
            ViewRemoteMessageHeaderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            headerImageLoadListener,
        )

        ITEM_TYPE_LIST_SECTION_TITLE -> RemoteMessageItemHolder.SectionTitleHolder(
            ViewRemoteMessageSectionTitleItemBinding.inflate(
                LayoutInflater.from(parent.context),
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
            is RemoteMessageItemHolder.HeaderViewHolder -> {
                holder.bind(getItem(position) as ModalListItem.Header)
            }

            is RemoteMessageItemHolder.CardItemViewHolder -> {
                holder.bind((getItem(position) as ModalListItem.CardListItem).cardItem as CardItem.ListItem)
            }

            is RemoteMessageItemHolder.FeaturedItemHolder -> {
                holder.bind((getItem(position) as ModalListItem.CardListItem).cardItem as CardItem.ListItem)
            }

            is RemoteMessageItemHolder.SectionTitleHolder -> {
                holder.bind((getItem(position) as ModalListItem.CardListItem).cardItem as CardItem.SectionTitle)
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is ModalListItem.Header -> ITEM_TYPE_HEADER
        is ModalListItem.CardListItem -> when (item.cardItem.type) {
            CardItemType.TWO_LINE_LIST_ITEM -> ITEM_TYPE_TWO_LINE_LIST_ITEM
            CardItemType.LIST_SECTION_TITLE -> ITEM_TYPE_LIST_SECTION_TITLE
            CardItemType.FEATURED_TWO_LINE_SINGLE_ACTION_LIST_ITEM -> ITEM_TYPE_FEATURED_TWO_LINE_LIST_ITEM
        }
    }

    sealed class RemoteMessageItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        class HeaderViewHolder(
            private val binding: ViewRemoteMessageHeaderBinding,
            private val listener: HeaderImageLoadListener?,
        ) : RemoteMessageItemHolder(binding.root) {

            fun bind(header: ModalListItem.Header) {
                binding.headerTitle.text = header.titleText

                if (!header.imageUrl.isNullOrEmpty()) {
                    val imageSource: Any = header.imageFilePath?.let { File(it) } ?: header.imageUrl

                    Glide.with(binding.remoteImage)
                        .load(imageSource)
                        .error(header.placeholder.drawable(true))
                        .addListener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>,
                                isFirstResource: Boolean,
                            ): Boolean {
                                listener?.onImageLoadFailed()
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean,
                            ): Boolean {
                                listener?.onImageLoadSuccess()
                                return false
                            }
                        })
                        .centerCrop()
                        .transition(withCrossFade())
                        .into(binding.remoteImage)
                    binding.headerImage.gone()
                    binding.remoteImage.show()
                } else {
                    binding.headerImage.setImageResource(header.placeholder.drawable(true))
                    binding.remoteImage.gone()
                    binding.headerImage.show()
                }
            }
        }

        class CardItemViewHolder(
            private val binding: ViewRemoteMessageCardItemBinding,
            private val listener: CardItemClickListener,
        ) : RemoteMessageItemHolder(binding.root) {

            fun bind(item: CardItem.ListItem) {
                binding.title.text = item.titleText
                binding.description.text = item.descriptionText
                binding.startImage.setImageResource(item.placeholder.drawable(true))
                binding.listItemContainer.setOnClickListener {
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

    interface HeaderImageLoadListener {
        fun onImageLoadSuccess()
        fun onImageLoadFailed()
    }

    private class ModalSurfaceDiffCallback : DiffUtil.ItemCallback<ModalListItem>() {
        override fun areItemsTheSame(
            oldItem: ModalListItem,
            newItem: ModalListItem,
        ): Boolean {
            return oldItem.id == newItem.id
        }

        @Suppress("DiffUtilEquals")
        override fun areContentsTheSame(
            oldItem: ModalListItem,
            newItem: ModalListItem,
        ): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val ITEM_TYPE_HEADER = 0
        private const val ITEM_TYPE_FEATURED_TWO_LINE_LIST_ITEM = 1
        private const val ITEM_TYPE_TWO_LINE_LIST_ITEM = 2
        private const val ITEM_TYPE_LIST_SECTION_TITLE = 3
    }
}
