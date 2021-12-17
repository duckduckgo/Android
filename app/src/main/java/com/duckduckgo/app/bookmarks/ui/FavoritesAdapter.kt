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

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewSavedSiteEmptyHintBinding
import com.duckduckgo.app.browser.databinding.ViewSavedSiteEntryBinding
import com.duckduckgo.app.browser.databinding.ViewSavedSiteSectionTitleBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import kotlinx.coroutines.launch

class FavoritesAdapter(
    private val layoutInflater: LayoutInflater,
    private val viewModel: BookmarksViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager
) :
    ListAdapter<FavoritesAdapter.FavoriteItemTypes, FavoritesScreenViewHolders>(
        FavoritesDiffCallback()) {

    companion object {
        const val FAVORITE_SECTION_TITLE_TYPE = 0
        const val EMPTY_STATE_TYPE = 1
        const val FAVORITE_TYPE = 2
    }

    interface FavoriteItemTypes
    object Header : FavoriteItemTypes
    object EmptyHint : FavoriteItemTypes
    data class FavoriteItem(val favorite: Favorite) : FavoriteItemTypes

    var favoriteItems: List<FavoriteItemTypes> = emptyList()
        set(value) {
            field = generateNewList(value)
            submitList(field)
        }

    private fun generateNewList(value: List<FavoriteItemTypes>): List<FavoriteItemTypes> {
        return listOf(Header) + (if (value.isEmpty()) listOf(EmptyHint) else value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoritesScreenViewHolders {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            FAVORITE_TYPE -> {
                val binding = ViewSavedSiteEntryBinding.inflate(inflater, parent, false)
                return FavoritesScreenViewHolders.FavoriteViewHolder(
                    layoutInflater, binding, viewModel, lifecycleOwner, faviconManager)
            }
            FAVORITE_SECTION_TITLE_TYPE -> {
                val binding = ViewSavedSiteSectionTitleBinding.inflate(inflater, parent, false)
                return FavoritesScreenViewHolders.SectionTitle(binding)
            }
            EMPTY_STATE_TYPE -> {
                val binding = ViewSavedSiteEmptyHintBinding.inflate(inflater, parent, false)
                FavoritesScreenViewHolders.EmptyHint(binding)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemCount(): Int {
        return favoriteItems.size
    }

    override fun onBindViewHolder(holder: FavoritesScreenViewHolders, position: Int) {
        when (holder) {
            is FavoritesScreenViewHolders.FavoriteViewHolder -> {
                holder.update((favoriteItems[position] as FavoriteItem).favorite)
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
        return when (favoriteItems[position]) {
            is Header -> {
                FAVORITE_SECTION_TITLE_TYPE
            }
            is EmptyHint -> {
                EMPTY_STATE_TYPE
            }
            else -> {
                FAVORITE_TYPE
            }
        }
    }
}

sealed class FavoritesScreenViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class SectionTitle(private val binding: ViewSavedSiteSectionTitleBinding) :
        FavoritesScreenViewHolders(binding.root) {
        fun bind() {
            binding.savedSiteSectionTitle.setText(R.string.favoritesSectionTitle)
        }
    }

    class EmptyHint(private val binding: ViewSavedSiteEmptyHintBinding) :
        FavoritesScreenViewHolders(binding.root) {
        fun bind() {
            binding.savedSiteEmptyHint.setText(R.string.favoritesEmptyHint)
        }
    }

    class FavoriteViewHolder(
        private val layoutInflater: LayoutInflater,
        private val binding: ViewSavedSiteEntryBinding,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : FavoritesScreenViewHolders(binding.root) {

        private val context: Context = binding.root.context
        lateinit var favorite: Favorite

        fun update(favorite: Favorite) {
            val listItem = binding.root
            this.favorite = favorite

            listItem.setContentDescription(
                context.getString(R.string.bookmarkOverflowContentDescription, favorite.title))

            listItem.setTitle(favorite.title)
            listItem.setSubtitle(parseDisplayUrl(favorite.url))
            loadFavicon(favorite.url)

            listItem.setOverflowClickListener { anchor -> showOverFlowMenu(anchor, favorite) }

            listItem.setClickListener { viewModel.onSelected(favorite) }
        }

        private fun loadFavicon(url: String) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalOrFallback(
                    url = url, view = itemView.findViewById(R.id.image))
            }
        }

        private fun parseDisplayUrl(urlString: String): String {
            val uri = Uri.parse(urlString)
            return uri.baseHost ?: return urlString
        }

        private fun showOverFlowMenu(anchor: View, favorite: Favorite) {
            val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_edit_delete_menu)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.findViewById(R.id.edit)) { editFavorite(favorite) }
                onMenuItemClicked(view.findViewById(R.id.delete)) { deleteFavorite(favorite) }
            }
            popupMenu.show(binding.root, anchor)
        }

        private fun editFavorite(favorite: Favorite) {
            viewModel.onEditSavedSiteRequested(favorite)
        }

        private fun deleteFavorite(favorite: Favorite) {
            viewModel.onDeleteSavedSiteRequested(favorite)
        }
    }
}
