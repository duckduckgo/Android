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

package com.duckduckgo.app.browser.favorites

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.LayoutParams
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.RecyclerView.State
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favorites.NewTabSectionsItem.FavouriteItem
import com.duckduckgo.app.browser.favorites.NewTabSectionsItem.PlaceholderItem
import com.duckduckgo.app.browser.favorites.NewTabSectionsItem.ShortcutItem
import com.duckduckgo.app.browser.favorites.NewTabShortcut.BOOKMARKS
import com.duckduckgo.app.browser.favorites.NewTabShortcut.CHAT
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.griditem.DaxNewTabGridItem.GridItemType.Favicon
import com.duckduckgo.common.ui.view.griditem.DaxNewTabGridItem.GridItemType.Placeholder
import com.duckduckgo.common.ui.view.griditem.DaxNewTabGridItem.GridItemType.Shortcut
import com.duckduckgo.mobile.android.databinding.RowNewTabGridItemBinding
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import timber.log.Timber

class NewTabSectionsAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val onMoveListener: (RecyclerView.ViewHolder) -> Unit,
    private val onShortcutSelected: (NewTabShortcut) -> Unit,
    private val onFavoriteSelected: (Favorite) -> Unit,
    private val onEditFavoriteSelected: (Favorite) -> Unit,
    private val onRemoveFavoriteSelected: (Favorite) -> Unit,
    private val onDeleteFavoriteSelected: (Favorite) -> Unit,
) : ListAdapter<NewTabSectionsItem, ViewHolder>(NewTabSectionsDiffCallback()) {

    var expanded: Boolean = false

    companion object {
        private const val PLACEHOLDER_VIEW_TYPE = 0
        private const val SHORTCUT_VIEW_TYPE = 1
        private const val FAVORITE_TYPE = 2

        val SHORTCUTS = listOf(
            ShortcutItem(BOOKMARKS),
            ShortcutItem(CHAT),
        )

        val PORTRAIT_PLACEHOLDERS = listOf(
            PlaceholderItem,
            PlaceholderItem,
            PlaceholderItem,
            PlaceholderItem,
        )

        val LANDSCAPE_PLACEHOLDERS = listOf(
            PlaceholderItem,
            PlaceholderItem,
            PlaceholderItem,
            PlaceholderItem,
            PlaceholderItem,
            PlaceholderItem,
        )
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PlaceholderItem -> PLACEHOLDER_VIEW_TYPE
            is ShortcutItem -> SHORTCUT_VIEW_TYPE
            is FavouriteItem -> FAVORITE_TYPE
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return when (viewType) {
            PLACEHOLDER_VIEW_TYPE -> PlaceholderViewHolder(
                RowNewTabGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )

            SHORTCUT_VIEW_TYPE -> ShortcutViewHolder(
                RowNewTabGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                onShortcutSelected,
            )

            FAVORITE_TYPE -> FavouriteViewHolder(
                RowNewTabGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                lifecycleOwner,
                faviconManager,
                onMoveListener,
                onFavoriteSelected,
                onEditFavoriteSelected,
                onRemoveFavoriteSelected,
                onDeleteFavoriteSelected,
            )

            else -> FavouriteViewHolder(
                RowNewTabGridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
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
            is ShortcutViewHolder -> holder.bind(getItem(position) as ShortcutItem)
            is FavouriteViewHolder -> holder.bind(getItem(position) as FavouriteItem)
        }
    }

    private class PlaceholderViewHolder(private val binding: RowNewTabGridItemBinding) : ViewHolder(binding.root) {
        fun bind() {
            binding.root.setItemType(Placeholder)
        }
    }

    private class ShortcutViewHolder(
        private val binding: RowNewTabGridItemBinding,
        private val onShortcutSelected: (NewTabShortcut) -> Unit,
    ) : ViewHolder(binding.root) {
        fun bind(
            item: ShortcutItem,
        ) {
            with(binding.root) {
                setItemType(Shortcut)
                setPrimaryText(item.shortcut.titleResource)
                setLeadingIconDrawable(item.shortcut.iconResource)
                setClickListener {
                    onShortcutSelected(item.shortcut)
                }
            }
        }
    }

    private class FavouriteViewHolder(
        private val binding: RowNewTabGridItemBinding,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager,
        private val onMoveListener: (RecyclerView.ViewHolder) -> Unit,
        private val onFavoriteSelected: (Favorite) -> Unit,
        private val onEditFavoriteSelected: (Favorite) -> Unit,
        private val onRemoveFavoriteSelected: (Favorite) -> Unit,
        private val onDeleteFavoriteSelected: (Favorite) -> Unit,
    ) : ViewHolder(binding.root), DragDropViewHolderListener {

        private var itemState: ItemState = ItemState.Stale
        private var popupMenu: PopupMenu? = null

        sealed class ItemState {
            object Stale : ItemState()
            object LongPress : ItemState()
            object Drag : ItemState()
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
            item: FavouriteItem,
        ) {
            with(binding.root) {
                setItemType(Favicon)
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
                Timber.d("New Tab: onLongClick")
                itemState = ItemState.LongPress
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
                        if (itemState != ItemState.LongPress) return@setTouchListener false

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
            itemState = ItemState.Drag
        }

        override fun onItemMoved(
            dX: Float,
            dY: Float,
        ) {
            if (itemState != ItemState.Drag) return

            if (dX.absoluteValue > 10 || dY.absoluteValue > 10) {
                popupMenu?.dismiss()
            }
        }

        override fun onItemReleased() {
            scaleDownFavicon()
            binding.root.showTitle()
            itemState = ItemState.Stale
        }
    }
}

sealed class NewTabSectionsItem {
    data object PlaceholderItem : NewTabSectionsItem()
    data class ShortcutItem(val shortcut: NewTabShortcut) : NewTabSectionsItem()
    data class FavouriteItem(val favorite: Favorite) : NewTabSectionsItem()
}

enum class NewTabShortcut(
    @StringRes val titleResource: Int,
    @DrawableRes val iconResource: Int,
) {
    BOOKMARKS(R.string.newTabPageShortcutBookmarks, com.duckduckgo.mobile.android.R.drawable.ic_bookmarks_open_color_16),
    CHAT(R.string.newTabPageShortcutChat, com.duckduckgo.mobile.android.R.drawable.ic_placeholder_color_16),
}

private class NewTabSectionsDiffCallback : DiffUtil.ItemCallback<NewTabSectionsItem>() {
    override fun areItemsTheSame(
        oldItem: NewTabSectionsItem,
        newItem: NewTabSectionsItem,
    ): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(
        oldItem: NewTabSectionsItem,
        newItem: NewTabSectionsItem,
    ): Boolean {
        return oldItem == newItem
    }
}

class ColumnItemDecoration : ItemDecoration() {
    // Horizontal padding
    private val padding = 0

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: State,
    ) {
        val layoutParams = view.layoutParams as LayoutParams
        val gridLayoutManager = parent.layoutManager as GridLayoutManager?
        val position = parent.getChildAdapterPosition(view)
        val spanSize = gridLayoutManager!!.spanSizeLookup.getSpanSize(position).toFloat()
        val totalSpanSize = gridLayoutManager.spanCount.toFloat()

        val n = totalSpanSize / spanSize // num columns
        val c = layoutParams.spanIndex / spanSize // column index

        val leftPadding = padding * ((n - c) / n)
        val rightPadding = padding * ((c + 1) / n)

        outRect.left = leftPadding.toInt()
        outRect.right = rightPadding.toInt()
    }
}

class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacing: Int,
    private val includeEdge: Boolean,
) : ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: State,
    ) {
        val position = parent.getChildAdapterPosition(view) // item position
        val column = position % spanCount // item column

        if (includeEdge) {
            outRect.left = spacing - column * spacing / spanCount // spacing - column * ((1f / spanCount) * spacing)
            outRect.right = (column + 1) * spacing / spanCount // (column + 1) * ((1f / spanCount) * spacing)

            if (position < spanCount) { // top edge
                outRect.top = spacing
            }
            outRect.bottom = spacing // item bottom
        } else {
            outRect.left = column * spacing / spanCount // column * ((1f / spanCount) * spacing)
            outRect.right = spacing - (column + 1) * spacing / spanCount // spacing - (column + 1) * ((1f /    spanCount) * spacing)
            if (position >= spanCount) {
                outRect.top = spacing // item top
            }
        }
    }
}
