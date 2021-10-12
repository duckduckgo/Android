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

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.ui.FavoritesAdapter
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewQuickAccessItemBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.QuickAccessViewHolder
import com.duckduckgo.app.browser.favorites.QuickAccessAdapterDiffCallback.Companion.DIFF_KEY_TITLE
import com.duckduckgo.app.browser.favorites.QuickAccessAdapterDiffCallback.Companion.DIFF_KEY_URL
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import kotlinx.android.synthetic.main.view_quick_access_item.view.*
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.absoluteValue

class FavoritesQuickAccessAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val onMoveListener: (RecyclerView.ViewHolder) -> Unit,
    private val onItemSelected: (QuickAccessFavorite) -> Unit,
    private val onEditClicked: (QuickAccessFavorite) -> Unit,
    private val onDeleteClicked: (QuickAccessFavorite) -> Unit
) : ListAdapter<QuickAccessFavorite, QuickAccessViewHolder>(QuickAccessAdapterDiffCallback()) {

    companion object {
        const val QUICK_ACCESS_ITEM_MAX_SIZE_DP = 90
    }

    data class QuickAccessFavorite(val favorite: SavedSite.Favorite) : FavoritesAdapter.FavoriteItemTypes

    class QuickAccessViewHolder(
        private val inflater: LayoutInflater,
        private val binding: ViewQuickAccessItemBinding,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager,
        private val onMoveListener: (RecyclerView.ViewHolder) -> Unit,
        private val onItemSelected: (QuickAccessFavorite) -> Unit,
        private val onEditClicked: (QuickAccessFavorite) -> Unit,
        private val onDeleteClicked: (QuickAccessFavorite) -> Unit
    ) : RecyclerView.ViewHolder(binding.root), DragDropViewHolderListener {

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
            PropertyValuesHolder.ofFloat("scaleY", 1.2f, 1f)
        ).apply {
            duration = 150L
        }
        private val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            binding.root,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f)
        ).apply {
            duration = 150L
        }

        fun bind(item: QuickAccessFavorite) {
            with(item.favorite) {
                binding.quickAccessTitle.text = title
                loadFavicon(url)
                configureClickListeners(item)
                configureTouchListener()
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun configureTouchListener() {
            binding.root.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (itemState != ItemState.LongPress) return@setOnTouchListener false

                        onMoveListener(this@QuickAccessViewHolder)
                    }
                    MotionEvent.ACTION_UP -> {
                        onItemReleased()
                    }
                }
                false
            }
        }

        fun bindFromPayload(item: QuickAccessFavorite, payloads: MutableList<Any>) {
            for (payload in payloads) {
                val bundle = payload as Bundle

                for (key: String in bundle.keySet()) {
                    Timber.v("$key changed - Need an update for $item")
                }

                bundle[DIFF_KEY_TITLE]?.let {
                    binding.quickAccessTitle.text = it as String
                }

                bundle[DIFF_KEY_URL]?.let {
                    loadFavicon(it as String)
                }

                configureClickListeners(item)
            }
        }

        private fun configureClickListeners(item: QuickAccessFavorite) {
            binding.root.setOnLongClickListener {
                itemState = ItemState.LongPress
                scaleUpFavicon()
                showOverFlowMenu(inflater, binding.quickAccessFaviconCard, item)
                false
            }
            binding.root.setOnClickListener { onItemSelected(item) }
        }

        private fun showOverFlowMenu(layoutInflater: LayoutInflater, anchor: View, item: QuickAccessFavorite) {
            val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_edit_delete_menu)
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.findViewById(R.id.edit)) { onEditClicked(item) }
                onMenuItemClicked(view.findViewById(R.id.delete)) { onDeleteClicked(item) }
            }
            popupMenu.showAnchoredToView(binding.root, anchor)
        }

        override fun onDragStarted() {
            scaleUpFavicon()
            binding.quickAccessTitle.alpha = 0f
            itemState = ItemState.Drag
        }

        override fun onItemMoved(dX: Float, dY: Float) {
            if (itemState != ItemState.Drag) return

            if (dX.absoluteValue > 10 || dY.absoluteValue > 10) {
                popupMenu?.dismiss()
            }
        }

        override fun onItemReleased() {
            scaleDownFavicon()
            binding.quickAccessTitle.alpha = 1f
            itemState = ItemState.Stale
        }

        private fun loadFavicon(url: String) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalOrFallback(url = url, view = binding.quickAccessFavicon)
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickAccessViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ViewQuickAccessItemBinding.inflate(inflater, parent, false)
        return QuickAccessViewHolder(inflater, binding, lifecycleOwner, faviconManager, onMoveListener, onItemSelected, onEditClicked, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: QuickAccessViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: QuickAccessViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        holder.bindFromPayload(getItem(position), payloads)
    }
}

class QuickAccessAdapterDiffCallback : DiffUtil.ItemCallback<QuickAccessFavorite>() {
    override fun areItemsTheSame(oldItem: QuickAccessFavorite, newItem: QuickAccessFavorite): Boolean {
        return oldItem.favorite.id == newItem.favorite.id
    }

    override fun areContentsTheSame(oldItem: QuickAccessFavorite, newItem: QuickAccessFavorite): Boolean {
        return oldItem.favorite.title == newItem.favorite.title &&
            oldItem.favorite.url == newItem.favorite.url &&
            oldItem.favorite.position == newItem.favorite.position
    }

    override fun getChangePayload(oldItem: QuickAccessFavorite, newItem: QuickAccessFavorite): Any {
        val diffBundle = Bundle()

        if (oldItem.favorite.title != newItem.favorite.title) {
            diffBundle.putString(DIFF_KEY_TITLE, newItem.favorite.title)
        }

        if (oldItem.favorite.url != newItem.favorite.url) {
            diffBundle.putString(DIFF_KEY_URL, newItem.favorite.url)
        }

        if (oldItem.favorite.position != newItem.favorite.position) {
            diffBundle.putInt(DIFF_KEY_POSITION, newItem.favorite.position)
        }

        return diffBundle
    }

    companion object {
        const val DIFF_KEY_TITLE = "title"
        const val DIFF_KEY_URL = "url"
        const val DIFF_KEY_POSITION = "position"
    }
}

interface DragDropViewHolderListener {
    fun onDragStarted()
    fun onItemMoved(dX: Float, dY: Float)
    fun onItemReleased()
}
