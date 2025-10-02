/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.newtab

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.RowFavouriteSectionItemBinding
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.newtab.FavouriteNewTabSectionItemView.FavouriteItemType
import com.duckduckgo.savedsites.impl.newtab
    .FavouriteNewTabSectionsItem.FavouriteItemFavourite
import com.duckduckgo.savedsites.impl.newtab.FavouriteNewTabSectionsItem.PlaceholderItemFavourite
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionsAdapter.FavouriteViewHolder.ItemState.Drag
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionsAdapter.FavouriteViewHolder.ItemState.LongPress
import com.duckduckgo.savedsites.impl.newtab.FavouritesNewTabSectionsAdapter.FavouriteViewHolder.ItemState.Stale
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class FavouritesNewTabSectionsAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val onMoveListener: (ViewHolder) -> Unit,
    private val onFavoriteSelected: (Favorite) -> Unit,
    private val onEditFavoriteSelected: (Favorite) -> Unit,
    private val onRemoveFavoriteSelected: (Favorite) -> Unit,
    private val onDeleteFavoriteSelected: (Favorite) -> Unit,
) : ListAdapter<FavouriteNewTabSectionsItem, ViewHolder>(NewTabSectionsDiffCallback()) {

    var expanded: Boolean = false

    companion object {
        private const val PLACEHOLDER_VIEW_TYPE = 0
        private const val FAVORITE_TYPE = 1
        const val QUICK_ACCESS_ITEM_MAX_SIZE_DP = 90
        const val QUICK_ACCESS_GRID_MAX_COLUMNS = 6

        val PORTRAIT_PLACEHOLDERS = listOf(
            PlaceholderItemFavourite,
            PlaceholderItemFavourite,
            PlaceholderItemFavourite,
            PlaceholderItemFavourite,
        )

        val LANDSCAPE_PLACEHOLDERS = listOf(
            PlaceholderItemFavourite,
            PlaceholderItemFavourite,
            PlaceholderItemFavourite,
            PlaceholderItemFavourite,
            PlaceholderItemFavourite,
            PlaceholderItemFavourite,
        )
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PlaceholderItemFavourite -> PLACEHOLDER_VIEW_TYPE
            is FavouriteItemFavourite -> FAVORITE_TYPE
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return when (viewType) {
            PLACEHOLDER_VIEW_TYPE -> PlaceholderViewHolder(
                RowFavouriteSectionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )

            FAVORITE_TYPE -> FavouriteViewHolder(
                RowFavouriteSectionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                lifecycleOwner,
                faviconManager,
                onMoveListener,
                onFavoriteSelected,
                onEditFavoriteSelected,
                onRemoveFavoriteSelected,
                onDeleteFavoriteSelected,
            )

            else -> FavouriteViewHolder(
                RowFavouriteSectionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                lifecycleOwner,
                faviconManager,
                onMoveListener,
                onFavoriteSelected,
                onEditFavoriteSelected,
                onRemoveFavoriteSelected,
                onDeleteFavoriteSelected,
            )
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is PlaceholderViewHolder -> holder.bind()
            is FavouriteViewHolder -> holder.bind(getItem(position) as FavouriteItemFavourite)
        }
    }

    private class PlaceholderViewHolder(private val binding: RowFavouriteSectionItemBinding) : ViewHolder(binding.root) {
        fun bind() {
            binding.root.setItemType(FavouriteItemType.Placeholder)
        }
    }

    private class FavouriteViewHolder(
        private val binding: RowFavouriteSectionItemBinding,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager,
        private val onMoveListener: (RecyclerView.ViewHolder) -> Unit,
        private val onFavoriteSelected: (Favorite) -> Unit,
        private val onEditFavoriteSelected: (Favorite) -> Unit,
        private val onRemoveFavoriteSelected: (Favorite) -> Unit,
        private val onDeleteFavoriteSelected: (Favorite) -> Unit,
    ) : ViewHolder(binding.root), DragDropViewHolderListener {

        private var itemState: ItemState = Stale
        private var popupMenu: PopupMenu? = null

        sealed class ItemState {
            data object Stale : ItemState()
            data object LongPress : ItemState()
            data object Drag : ItemState()
        }

        private val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            binding.root,
            PropertyValuesHolder.ofFloat("scaleX", 1.2f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1.2f, 1f),
        ).apply {
            duration = 150L
        }
        private val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            binding.root,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f),
        ).apply {
            duration = 150L
        }

        fun bind(
            item: FavouriteItemFavourite,
        ) {
            with(binding.root) {
                setItemType(FavouriteItemType.Favicon)
                setPrimaryText(item.favorite.title)
                loadFavicon(item.favorite.url)
                configureClickListeners(item.favorite)
                configureTouchListener()
            }
        }

        private fun loadFavicon(url: String) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewMaybeFromRemoteWithPlaceholder(url = url, view = binding.root.favicon())
            }
        }

        private fun scaleUpFavicon() {
            if (binding.root.scaleX == 1f) {
                scaleUp.start()
            }
        }

        private fun scaleDownFavicon() {
            if (binding.root.scaleX != 1.0f) {
                scaleDown.start()
            }
        }

        private fun configureClickListeners(favorite: Favorite) {
            binding.root.setLongClickListener {
                itemState = LongPress
                scaleUpFavicon()
                showOverFlowMenu(binding.root, favorite)
                false
            }
            binding.root.setClickListener { onFavoriteSelected(favorite) }
        }

        private fun showOverFlowMenu(
            anchor: View,
            favorite: Favorite,
        ) {
            val popupMenu = PopupMenu(LayoutInflater.from(anchor.context), R.layout.popup_window_edit_remove_favorite_delete_menu)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.findViewById(R.id.edit)) { onEditFavoriteSelected(favorite) }
                onMenuItemClicked(view.findViewById(R.id.removeFromFavorites)) { onRemoveFavoriteSelected(favorite) }
                onMenuItemClicked(view.findViewById(R.id.delete)) { onDeleteFavoriteSelected(favorite) }
            }
            popupMenu.showAnchoredToView(binding.root, anchor)
            this.popupMenu = popupMenu
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun configureTouchListener() {
            binding.root.setTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (itemState != LongPress) return@setTouchListener false

                        onMoveListener(this@FavouriteViewHolder)
                    }

                    MotionEvent.ACTION_UP -> {
                        onItemReleased()
                    }
                }
                false
            }
        }

        override fun onDragStarted() {
            scaleUpFavicon()
            binding.root.hideTitle()
            itemState = Drag
        }

        override fun onItemMoved(
            dX: Float,
            dY: Float,
        ) {
            if (itemState != Drag) return

            if (dX.absoluteValue > 10 || dY.absoluteValue > 10) {
                popupMenu?.dismiss()
            }
        }

        override fun onItemReleased() {
            scaleDownFavicon()
            binding.root.showTitle()
            itemState = Stale
        }
    }
}

sealed class FavouriteNewTabSectionsItem {
    data object PlaceholderItemFavourite : FavouriteNewTabSectionsItem()
    data class FavouriteItemFavourite(val favorite: Favorite) : FavouriteNewTabSectionsItem()
}

private class NewTabSectionsDiffCallback : DiffUtil.ItemCallback<FavouriteNewTabSectionsItem>() {
    override fun areItemsTheSame(
        oldItem: FavouriteNewTabSectionsItem,
        newItem: FavouriteNewTabSectionsItem,
    ): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(
        oldItem: FavouriteNewTabSectionsItem,
        newItem: FavouriteNewTabSectionsItem,
    ): Boolean {
        return oldItem == newItem
    }
}

interface DragDropViewHolderListener {
    fun onDragStarted()
    fun onItemMoved(
        dX: Float,
        dY: Float,
    )

    fun onItemReleased()
}
