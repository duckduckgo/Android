/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.model.Favorite
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.baseHost
import kotlinx.android.synthetic.main.popup_window_bookmarks_menu.view.*
import kotlinx.android.synthetic.main.view_bookmark_entry.view.*
import kotlinx.android.synthetic.main.view_boomark_empty_hint.view.*
import kotlinx.android.synthetic.main.view_fireproof_website_empty_hint.view.*
import kotlinx.android.synthetic.main.view_fireproof_website_empty_hint.view.fireproofWebsiteEmptyHint
import kotlinx.android.synthetic.main.view_location_permissions_section_title.view.*
import kotlinx.coroutines.launch
import timber.log.Timber

class FavoritesAdapter(
    private val layoutInflater: LayoutInflater,
    private val viewModel: BookmarksViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager
) : RecyclerView.Adapter<FavoritesScreenViewHolders>() {

    companion object {
        const val FAVORITE_SECTION_TITLE_TYPE = 0
        const val EMPTY_STATE_TYPE = 1
        const val FAVORITE_TYPE = 2

        const val FAVORITE_SECTION_TITLE_SIZE = 1
        const val FAVORITE_EMPTY_HINT_SIZE = 1
    }

    var favoriteItems: List<Favorite> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoritesScreenViewHolders {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            BookmarksAdapter.BOOKMARK_TYPE -> {
                val view = inflater.inflate(R.layout.view_bookmark_entry, parent, false)
                return FavoritesScreenViewHolders.FavoriteViewHolder(layoutInflater, view, viewModel, lifecycleOwner, faviconManager)
            }
            BookmarksAdapter.BOOKMARK_SECTION_TITLE_TYPE -> {
                val view = inflater.inflate(R.layout.view_location_permissions_section_title, parent, false)
                return FavoritesScreenViewHolders.SectionTitle(view)
            }
            BookmarksAdapter.EMPTY_STATE_TYPE -> {
                val view = inflater.inflate(R.layout.view_boomark_empty_hint, parent, false)
                FavoritesScreenViewHolders.EmptyHint(view)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemCount(): Int {
        return headerItemsSize() + listSize()
    }

    override fun onBindViewHolder(holder: FavoritesScreenViewHolders, position: Int) {
        when (holder) {
            is FavoritesScreenViewHolders.FavoriteViewHolder -> {
                holder.update(favoriteItems[position - headerItemsSize()])
            }
            is FavoritesScreenViewHolders.SectionTitle -> {
                holder.bind()
            }
            is FavoritesScreenViewHolders.EmptyHint -> {
                holder.bind()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position == 0 -> {
                FAVORITE_SECTION_TITLE_TYPE
            }
            favoriteItems.isEmpty() -> {
                EMPTY_STATE_TYPE
            }
            else -> {
                FAVORITE_TYPE
            }
        }
    }

    private fun headerItemsSize(): Int {
        return FAVORITE_SECTION_TITLE_SIZE
    }

    private fun listSize() = if (favoriteItems.isEmpty()) FAVORITE_EMPTY_HINT_SIZE else favoriteItems.size
}

sealed class FavoritesScreenViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class SectionTitle(itemView: View) : FavoritesScreenViewHolders(itemView) {
        fun bind() {
            itemView.locationPermissionsSectionTitle.setText(R.string.favoritesSectionTitle)
        }
    }

    class EmptyHint(itemView: View) : FavoritesScreenViewHolders(itemView) {
        fun bind() {
            itemView.bookmarksEmptyHint.setText(R.string.favoritesEmptyHint)
        }
    }

    class FavoriteViewHolder(
        private val layoutInflater: LayoutInflater,
        itemView: View,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : FavoritesScreenViewHolders(itemView) {

        lateinit var favorite: Favorite

        fun update(favorite: Favorite) {
            this.favorite = favorite

            itemView.overflowMenu.contentDescription = itemView.context.getString(
                R.string.bookmarkOverflowContentDescription,
                favorite.title
            )

            itemView.title.text = favorite.title
            itemView.url.text = parseDisplayUrl(favorite.url)
            loadFavicon(favorite.url)

            itemView.overflowMenu.setOnClickListener {
                showOverFlowMenu(itemView.overflowMenu, favorite)
            }

            itemView.setOnClickListener {
                viewModel.onSelected(favorite)
            }
        }

        private fun loadFavicon(url: String) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromPersisted(url, itemView.favicon)
            }
        }

        private fun parseDisplayUrl(urlString: String): String {
            val uri = Uri.parse(urlString)
            return uri.baseHost ?: return urlString
        }

        private fun showOverFlowMenu(anchor: ImageView, favorite: Favorite) {
            val popupMenu = BookmarksPopupMenu(layoutInflater)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.editBookmark) { editFavorite(favorite) }
                onMenuItemClicked(view.deleteBookmark) { deleteFavorite(favorite) }
            }
            popupMenu.show(itemView, anchor)
        }

        private fun editFavorite(favorite: Favorite) {
            Timber.i("Editing favorite ${favorite.title}")
            viewModel.onEditFavoriteRequested(favorite)
        }

        private fun deleteFavorite(favorite: Favorite) {
            Timber.i("Deleting favorite ${favorite.title}")
            viewModel.onDeleteRequested(favorite)
        }
    }
}