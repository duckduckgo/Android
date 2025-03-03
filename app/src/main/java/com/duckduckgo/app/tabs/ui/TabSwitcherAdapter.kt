/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ItemGridTrackerAnimationTileBinding
import com.duckduckgo.app.browser.databinding.ItemListTrackerAnimationTileBinding
import com.duckduckgo.app.browser.databinding.ItemTabGridBinding
import com.duckduckgo.app.browser.databinding.ItemTabListBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_ALPHA
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_PREVIEW
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_TITLE
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_URL
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_VIEWED
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.GRID_TAB
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.GRID_TRACKER_ANIMATION_TILE
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.LIST_TAB
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.LIST_TRACKER_ANIMATION_TILE
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.TabViewHolder
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackerAnimationTile.ANIMATED_TILE_DEFAULT_ALPHA
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackerAnimationTile.ANIMATED_TILE_NO_REPLACE_ALPHA
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.swap
import java.io.File
import kotlin.Int
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class TabSwitcherAdapter(
    private val itemClickListener: TabSwitcherListener,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val dispatchers: DispatcherProvider,
    private val trackerCountAnimator: TrackerCountAnimator,
) : Adapter<ViewHolder>() {

    private val list = mutableListOf<TabSwitcherItem>()
    private var isDragging: Boolean = false
    private var layoutType: LayoutType = LayoutType.GRID

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return list[position].id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            GRID_TAB -> {
                val binding = ItemTabGridBinding.inflate(inflater, parent, false)
                return TabSwitcherViewHolder.GridTabViewHolder(binding)
            }
            LIST_TAB -> {
                val binding = ItemTabListBinding.inflate(inflater, parent, false)
                return TabSwitcherViewHolder.ListTabViewHolder(binding)
            }
            GRID_TRACKER_ANIMATION_TILE -> {
                val binding = ItemGridTrackerAnimationTileBinding.inflate(inflater, parent, false)
                return TabSwitcherViewHolder.GridTrackerAnimationTileViewHolder(binding)
            }
            LIST_TRACKER_ANIMATION_TILE -> {
                val binding = ItemListTrackerAnimationTileBinding.inflate(inflater, parent, false)
                return TabSwitcherViewHolder.ListTrackerAnimationTileViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int =
        when (list[position]) {
            is TabSwitcherItem.Tab -> {
                when (layoutType) {
                    LayoutType.GRID -> GRID_TAB
                    LayoutType.LIST -> LIST_TAB
                }
            }
            is TabSwitcherItem.TrackerAnimationTile -> {
                when (layoutType) {
                    LayoutType.GRID -> GRID_TRACKER_ANIMATION_TILE
                    LayoutType.LIST -> LIST_TRACKER_ANIMATION_TILE
                }
            }
        }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (holder) {
            is TabSwitcherViewHolder.GridTabViewHolder -> {
                val tab = (list[position] as TabSwitcherItem.Tab).tabEntity
                bindGridTab(holder, tab)
            }
            is TabSwitcherViewHolder.ListTabViewHolder -> {
                val tab = (list[position] as TabSwitcherItem.Tab).tabEntity
                bindListTab(holder, tab)
            }
            is TabSwitcherViewHolder.GridTrackerAnimationTileViewHolder -> {
                val trackerAnimationTile = list[position] as TabSwitcherItem.TrackerAnimationTile

                trackerCountAnimator.animateTrackersBlockedCountView(
                    context = holder.binding.root.context,
                    stringRes = R.string.trackersBlockedInTheLast7days,
                    totalTrackerCount = trackerAnimationTile.trackerCount,
                    trackerTextView = holder.binding.text,
                )
                holder.binding.close.setOnClickListener {
                    // TODO delete
                }
            }
            is TabSwitcherViewHolder.ListTrackerAnimationTileViewHolder -> {
                val trackerAnimationTile = list[position] as TabSwitcherItem.TrackerAnimationTile

                trackerCountAnimator.animateTrackersBlockedCountView(
                    context = holder.binding.root.context,
                    stringRes = R.string.trackersBlocked,
                    totalTrackerCount = trackerAnimationTile.trackerCount,
                    trackerTextView = holder.binding.title,
                )
                holder.binding.close.setOnClickListener {
                    // TODO delete
                }
            }
            else -> throw IllegalArgumentException("Unknown ViewHolder type: $holder")
        }
    }

    @VisibleForTesting
    fun createCloseClickListener(
        bindingAdapterPosition: () -> Int,
        tabSwitcherListener: TabSwitcherListener,
    ): View.OnClickListener {
        return View.OnClickListener {
            val position = bindingAdapterPosition()
            if (position != RecyclerView.NO_POSITION) {
                tabSwitcherListener.onTabDeleted(position = position, deletedBySwipe = false)
            }
        }
    }

    private fun bindListTab(holder: TabSwitcherViewHolder.ListTabViewHolder, tab: TabEntity) {
        val context = holder.binding.root.context
        holder.title.text = extractTabTitle(tab, context)
        holder.url.text = tab.url ?: ""
        holder.url.visibility = if (tab.url.isNullOrEmpty()) View.GONE else View.VISIBLE
        updateUnreadIndicator(holder, tab)
        loadFavicon(tab, holder.favicon)
        attachTabClickListeners(
            tabViewHolder = holder,
            bindingAdapterPosition = { holder.bindingAdapterPosition },
            tab = tab,
        )
    }

    private fun bindGridTab(holder: TabSwitcherViewHolder.GridTabViewHolder, tab: TabEntity) {
        val context = holder.binding.root.context
        val glide = Glide.with(context)
        holder.title.text = extractTabTitle(tab, context)
        updateUnreadIndicator(holder, tab)
        loadFavicon(tab, holder.favicon)
        loadTabPreviewImage(tab, glide, holder)
        attachTabClickListeners(
            tabViewHolder = holder,
            bindingAdapterPosition = { holder.bindingAdapterPosition },
            tab = tab,
        )
    }

    private fun extractTabTitle(tab: TabEntity, context: Context): String {
        var title = tab.displayTitle(context)
        title = title.removeSuffix(DUCKDUCKGO_TITLE_SUFFIX)
        return title
    }

    private fun updateUnreadIndicator(holder: TabViewHolder, tab: TabEntity) {
        holder.tabUnread.visibility = if (tab.viewed) View.INVISIBLE else View.VISIBLE
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }

        when (holder.itemViewType) {
            GRID_TAB -> handlePayloadsForGridTab(
                viewHolder = holder as TabSwitcherViewHolder.GridTabViewHolder,
                tab = list[position] as TabSwitcherItem.Tab,
                payloads = payloads,
            )
            LIST_TAB -> handlePayloadsForListTab(
                viewHolder = holder as TabSwitcherViewHolder.ListTabViewHolder,
                tab = list[position] as TabSwitcherItem.Tab,
                payloads = payloads,
            )
            GRID_TRACKER_ANIMATION_TILE, LIST_TRACKER_ANIMATION_TILE -> {
                for (payload in payloads) {
                    val bundle = payload as Bundle
                    holder.itemView.alpha = bundle.getFloat(DIFF_ALPHA, ANIMATED_TILE_DEFAULT_ALPHA)
                }
            }
        }
    }

    private fun handlePayloadsForGridTab(
        viewHolder: TabSwitcherViewHolder.GridTabViewHolder,
        tab: TabSwitcherItem.Tab,
        payloads: MutableList<Any>,
    ) {
        for (payload in payloads) {
            val bundle = payload as Bundle
            for (key in bundle.keySet()) {
                Timber.v("$key changed - Need an update for ${tab.tabEntity}")
            }

            if (bundle.containsKey(DIFF_KEY_PREVIEW)) {
                loadTabPreviewImage(tab.tabEntity, Glide.with(viewHolder.rootView), viewHolder)
            }

            bundle.getString(DIFF_KEY_TITLE)?.let {
                viewHolder.title.text = it
            }

            if (bundle.containsKey(DIFF_KEY_VIEWED)) {
                updateUnreadIndicator(viewHolder, tab.tabEntity)
            }
        }
    }

    private fun handlePayloadsForListTab(
        viewHolder: TabSwitcherViewHolder.ListTabViewHolder,
        tab: TabSwitcherItem.Tab,
        payloads: MutableList<Any>,
    ) {
        for (payload in payloads) {
            val bundle = payload as Bundle
            for (key in bundle.keySet()) {
                Timber.v("$key changed - Need an update for ${tab.tabEntity}")
            }

            bundle.getString(DIFF_KEY_URL)?.let {
                viewHolder.url.show()
                viewHolder.url.text = it
            }

            bundle.getString(DIFF_KEY_TITLE)?.let {
                viewHolder.title.text = it
            }

            if (bundle.containsKey(DIFF_KEY_VIEWED)) {
                updateUnreadIndicator(viewHolder, tab.tabEntity)
            }
        }
    }

    private fun loadFavicon(tab: TabEntity, view: ImageView) {
        val url = tab.url ?: return
        lifecycleOwner.lifecycleScope.launch {
            faviconManager.loadToViewFromLocalWithPlaceholder(tab.tabId, url, view)
        }
    }

    private fun loadTabPreviewImage(tab: TabEntity, glide: RequestManager, holder: TabSwitcherViewHolder.GridTabViewHolder) {
        val previewFile = tab.tabPreviewFile ?: return glide.clear(holder.tabPreview)

        lifecycleOwner.lifecycleScope.launch {
            val cachedWebViewPreview = withContext(dispatchers.io()) {
                File(webViewPreviewPersister.fullPathForFile(tab.tabId, previewFile)).takeIf { it.exists() }
            }

            if (cachedWebViewPreview == null) {
                glide.clear(holder.tabPreview)
                return@launch
            }

            glide.load(cachedWebViewPreview)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.tabPreview)

            holder.tabPreview.show()
        }
    }

    private fun attachTabClickListeners(tabViewHolder: TabViewHolder, bindingAdapterPosition: () -> Int, tab: TabEntity) {
        tabViewHolder.rootView.setOnClickListener {
            if (!isDragging) {
                itemClickListener.onTabSelected(tab)
            }
        }
        tabViewHolder.close.setOnClickListener(
            createCloseClickListener(
                bindingAdapterPosition = bindingAdapterPosition,
                tabSwitcherListener = itemClickListener,
            ),
        )
    }

    fun updateData(updatedList: List<TabSwitcherItem>) {
        val diffResult = DiffUtil.calculateDiff(TabSwitcherItemDiffCallback(list, updatedList, isDragging))
        list.clear()
        list.addAll(updatedList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun getTabSwitcherItem(position: Int): TabSwitcherItem? = list.getOrNull(position)

    fun adapterPositionForTab(tabId: String?): Int = list.indexOfFirst {
        it is TabSwitcherItem.Tab && it.tabEntity.tabId == tabId
    }

    fun onDraggingStarted() {
        isDragging = true
        updateAnimatedTileAlpha(ANIMATED_TILE_NO_REPLACE_ALPHA)
    }

    fun onDraggingFinished() {
        isDragging = false
        updateAnimatedTileAlpha(ANIMATED_TILE_DEFAULT_ALPHA)
    }

    private fun updateAnimatedTileAlpha(alpha: Float) {
        val animatedTilePosition = list.indexOfFirst { it is TabSwitcherItem.TrackerAnimationTile }
        if (animatedTilePosition != -1) {
            notifyItemChanged(
                animatedTilePosition,
                Bundle().apply {
                    putFloat(DIFF_ALPHA, alpha)
                },
            )
        }
    }

    fun onTabMoved(from: Int, to: Int) {
        val swapped = list.swap(from, to)
        updateData(swapped)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onLayoutTypeChanged(layoutType: LayoutType) {
        this.layoutType = layoutType
        notifyDataSetChanged()
    }

    companion object {
        private const val DUCKDUCKGO_TITLE_SUFFIX = "at DuckDuckGo"
    }

    sealed class TabSwitcherViewHolder(rootView: View) : ViewHolder(rootView) {

        companion object {
            const val GRID_TAB = 0
            const val LIST_TAB = 1
            const val GRID_TRACKER_ANIMATION_TILE = 2
            const val LIST_TRACKER_ANIMATION_TILE = 3
        }

        interface TabViewHolder {
            val rootView: View
            val favicon: ImageView
            val title: TextView
            val close: ImageView
            val tabUnread: ImageView
        }

        data class GridTabViewHolder(
            val binding: ItemTabGridBinding,
            override val rootView: View = binding.root,
            override val favicon: ImageView = binding.favicon,
            override val title: TextView = binding.title,
            override val close: ImageView = binding.close,
            override val tabUnread: ImageView = binding.tabUnread,
            val tabPreview: ImageView = binding.tabPreview,
        ) : TabSwitcherViewHolder(binding.root), TabViewHolder

        data class ListTabViewHolder(
            val binding: ItemTabListBinding,
            override val rootView: View = binding.root,
            override val favicon: ImageView = binding.favicon,
            override val title: TextView = binding.title,
            override val close: ImageView = binding.close,
            override val tabUnread: ImageView = binding.tabUnread,
            val url: TextView = binding.url,
        ) : TabSwitcherViewHolder(binding.root), TabViewHolder

        data class GridTrackerAnimationTileViewHolder(
            val binding: ItemGridTrackerAnimationTileBinding,
        ) : TabSwitcherViewHolder(binding.root)

        data class ListTrackerAnimationTileViewHolder(
            val binding: ItemListTrackerAnimationTileBinding,
        ) : TabSwitcherViewHolder(binding.root)
    }
}
