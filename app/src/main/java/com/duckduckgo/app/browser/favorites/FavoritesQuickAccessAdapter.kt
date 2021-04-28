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

package com.duckduckgo.app.browser.favorites

import android.os.Handler
import android.view.*
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.ui.FavoritesAdapter
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.QuickAccessViewHolder
import com.duckduckgo.app.global.baseHost
import kotlinx.android.synthetic.main.view_quick_access_item.view.*
import kotlinx.coroutines.launch
import timber.log.Timber

class FavoritesQuickAccessAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val onMoveListener: (RecyclerView.ViewHolder) -> Unit,
    private val onItemSelected: (QuickAccessFavorite) -> Unit,
    private val onEditClicked: (QuickAccessFavorite) -> Unit,
    private val onDeleteClicked: (QuickAccessFavorite) -> Unit
) : ListAdapter<QuickAccessFavorite, QuickAccessViewHolder>(QuickAccessAdapterDiffCallback()) {

    companion object {
        const val QUICK_ACCESS_ITEM_MAX_SIZE_DP = 100
    }

    data class QuickAccessFavorite(val favorite: SavedSite.Favorite) : FavoritesAdapter.FavoriteItemTypes

    class QuickAccessViewHolder(
        private val layoutInflater: LayoutInflater,
        itemView: View,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager,
        private val onMoveListener: (RecyclerView.ViewHolder) -> Unit,
        private val onItemSelected: (QuickAccessFavorite) -> Unit,
        private val onEditClicked: (QuickAccessFavorite) -> Unit,
        private val onDeleteClicked: (QuickAccessFavorite) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private var menu: Menu? = null

        fun bind(item: QuickAccessFavorite) {
            with(item.favorite) {
                itemView.quickAccessTitle.text = title
                loadFavicon(url)
                itemView.quickAccessFaviconCard.setOnLongClickListener {
                    Timber.i("QuickAccessFav: longPress")
                    false
                }

                //itemView.quickAccessFaviconImage.name = item.favorite.url.toUri().baseHost ?: ""

                itemView.quickAccessFaviconCard.setOnTouchListener { v, event ->
                    if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        Timber.i("QuickAccessFav: move")
                        onMoveListener(this@QuickAccessViewHolder)
                        Handler().post { menu?.close() }
                    }
                    false
                }

                itemView.quickAccessFaviconCard.setOnCreateContextMenuListener { menu, v, menuInfo ->
                    this@QuickAccessViewHolder.menu = menu
                    Timber.i("QuickAccessFav: setOnCreateContextMenuListener")
                    val editMenuItem: MenuItem = menu.add(Menu.NONE, 1, 1, "Edit")
                    val deleteMenuItem: MenuItem = menu.add(Menu.NONE, 2, 2, "Delete")
                    editMenuItem.setOnMenuItemClickListener {
                        onEditClicked(item)
                        true
                    }

                    deleteMenuItem.setOnMenuItemClickListener {
                        onDeleteClicked(item)
                        true
                    }
                }

                itemView.quickAccessFaviconCard.setOnClickListener { onItemSelected(item) }
            }
        }

        private fun loadFavicon(url: String) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromPersisted(url, itemView.quickAccessFavicon)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickAccessViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.view_quick_access_item, parent, false)
        return QuickAccessViewHolder(inflater, view, lifecycleOwner, faviconManager, onMoveListener, onItemSelected, onEditClicked, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: QuickAccessViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class QuickAccessAdapterDiffCallback : DiffUtil.ItemCallback<QuickAccessFavorite>() {
    override fun areItemsTheSame(oldItem: QuickAccessFavorite, newItem: QuickAccessFavorite): Boolean {
        return oldItem.favorite.id == newItem.favorite.id
    }

    override fun areContentsTheSame(oldItem: QuickAccessFavorite, newItem: QuickAccessFavorite): Boolean {
        return oldItem.favorite.title == newItem.favorite.title &&
                oldItem.favorite.url == newItem.favorite.url
    }
}
