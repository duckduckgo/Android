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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.duckduckgo.app.browser.databinding.ItemTabGridBinding
import com.duckduckgo.app.browser.databinding.ItemTabListBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.TabEntityDiffCallback
import com.duckduckgo.app.browser.tabpreview.TabEntityDiffCallback.Companion.DIFF_KEY_PREVIEW
import com.duckduckgo.app.browser.tabpreview.TabEntityDiffCallback.Companion.DIFF_KEY_TITLE
import com.duckduckgo.app.browser.tabpreview.TabEntityDiffCallback.Companion.DIFF_KEY_URL
import com.duckduckgo.app.browser.tabpreview.TabEntityDiffCallback.Companion.DIFF_KEY_VIEWED
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabViewHolder
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabViewHolder.GridTabViewHolder
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabViewHolder.ListTabViewHolder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.swap
import java.io.File
import kotlinx.coroutines.launch
import timber.log.Timber

class TabSwitcherAdapter(
    private val itemClickListener: TabSwitcherListener,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
) : Adapter<TabViewHolder>() {

    private val list = mutableListOf<TabEntity>()

    private var isDragging: Boolean = false
    private var layoutType: LayoutType = LayoutType.GRID

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): TabViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        when (layoutType) {
            LayoutType.GRID -> {
                val binding = ItemTabGridBinding.inflate(inflater, parent, false)
                return GridTabViewHolder(
                    binding = binding,
                    favicon = binding.favicon,
                    tabPreview = binding.tabPreview,
                    title = binding.title,
                    close = binding.close,
                    cardContentsContainer = binding.cardContentsContainer,
                    tabUnread = binding.tabUnread,
                )
            }
            LayoutType.LIST -> {
                val binding = ItemTabListBinding.inflate(inflater, parent, false)

                return ListTabViewHolder(
                    binding = binding,
                    favicon = binding.favicon,
                    title = binding.title,
                    url = binding.url,
                    close = binding.close,
                    cardContentsContainer = binding.cardContentsContainer,
                    tabUnread = binding.tabUnread,
                )
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return layoutType.ordinal
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(
        holder: TabViewHolder,
        position: Int,
    ) {
        when (holder) {
            is GridTabViewHolder -> bindGridTab(holder, position)
            is ListTabViewHolder -> bindListTab(holder, position)
        }
    }

    private fun bindListTab(
        holder: ListTabViewHolder,
        position: Int,
    ) {
        val context = holder.binding.root.context
        val tab = list[position]

        holder.title.text = extractTabTitle(tab, context)

        if (tab.url.isNullOrEmpty()) {
            holder.url.gone()
        } else {
            holder.url.text = tab.url
            holder.url.show()
        }

        updateUnreadIndicator(holder, tab)
        loadFavicon(tab, holder.favicon)
        attachClickListeners(holder, tab)
    }

    private fun bindGridTab(
        holder: GridTabViewHolder,
        position: Int,
    ) {
        val context = holder.binding.root.context
        val tab = list[position]
        val glide = Glide.with(context)

        holder.title.text = extractTabTitle(tab, context)

        updateUnreadIndicator(holder, tab)
        loadFavicon(tab, holder.favicon)
        loadTabPreviewImage(tab, glide, holder)
        attachClickListeners(holder, tab)
    }

    private fun extractTabTitle(
        tab: TabEntity,
        context: Context,
    ): String {
        var title = tab.displayTitle(context)
        title = title.removeSuffix(DUCKDUCKGO_TITLE_SUFFIX)
        return title
    }

    private fun updateUnreadIndicator(
        holder: TabViewHolder,
        tab: TabEntity,
    ) {
        holder.tabUnread.visibility = if (tab.viewed) View.INVISIBLE else View.VISIBLE
    }

    override fun onBindViewHolder(
        holder: TabViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }

        val tab = list[position]

        for (payload in payloads) {
            val bundle = payload as Bundle

            for (key: String in bundle.keySet()) {
                Timber.v("$key changed - Need an update for $tab")
            }

            when (holder) {
                is GridTabViewHolder -> {
                    if (bundle.containsKey(DIFF_KEY_PREVIEW)) {
                        loadTabPreviewImage(tab, Glide.with(holder.rootView), holder)
                    }
                }
                is ListTabViewHolder -> {
                    bundle.getString(DIFF_KEY_URL)?.let {
                        holder.url.show()
                        holder.url.text = it
                    }
                }
            }

            bundle.getString(DIFF_KEY_TITLE)?.let {
                holder.title.text = it
            }

            if (bundle.containsKey(DIFF_KEY_VIEWED)) {
                updateUnreadIndicator(holder, tab)
            }
        }
    }

    private fun loadFavicon(
        tab: TabEntity,
        view: ImageView,
    ) {
        val url = tab.url ?: return
        lifecycleOwner.lifecycleScope.launch {
            faviconManager.loadToViewFromLocalWithPlaceholder(tab.tabId, url, view)
        }
    }

    private fun loadTabPreviewImage(
        tab: TabEntity,
        glide: RequestManager,
        holder: GridTabViewHolder,
    ) {
        val previewFile = tab.tabPreviewFile
        if (previewFile == null) {
            glide.clear(holder.tabPreview)
            return
        }

        val cachedWebViewPreview = File(webViewPreviewPersister.fullPathForFile(tab.tabId, previewFile))
        if (!cachedWebViewPreview.exists()) {
            glide.clear(holder.tabPreview)
            return
        }

        glide.load(cachedWebViewPreview)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.tabPreview)

        holder.tabPreview.show()
    }

    private fun attachClickListeners(
        holder: TabViewHolder,
        tab: TabEntity,
    ) {
        holder.rootView.setOnClickListener {
            if (!isDragging) {
                itemClickListener.onTabSelected(tab)
            }
        }
        holder.close.setOnClickListener {
            itemClickListener.onTabDeleted(holder.bindingAdapterPosition, false)
        }
    }

    fun updateData(data: List<TabEntity>?) {
        if (data != null) {
            submitList(data)
        }
    }

    private fun submitList(updatedList: List<TabEntity>) {
        val diffResult = DiffUtil.calculateDiff(TabEntityDiffCallback(list, updatedList))

        list.clear()
        list.addAll(updatedList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun getTab(position: Int): TabEntity? = list.getOrNull(position)

    fun adapterPositionForTab(tabId: String?): Int {
        if (tabId == null) return -1
        return list.indexOfFirst { it.tabId == tabId }
    }

    fun onDraggingStarted() {
        isDragging = true
    }

    fun onDraggingFinished() {
        isDragging = false
    }

    fun onTabMoved(from: Int, to: Int) {
        val swapped = list.swap(from, to)
        list.clear()
        list.addAll(swapped)
        notifyItemMoved(from, to)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onLayoutTypeChanged(layoutType: LayoutType) {
        this.layoutType = layoutType
        notifyDataSetChanged()
    }

    companion object {
        private const val DUCKDUCKGO_TITLE_SUFFIX = "at DuckDuckGo"
    }

    sealed class TabViewHolder(
        val rootView: View,
        open val favicon: ImageView,
        open val title: TextView,
        open val close: ImageView,
        open val tabUnread: ImageView,
        open val cardContentsContainer: ViewGroup,
    ) : ViewHolder(rootView) {
        data class GridTabViewHolder(
            val binding: ItemTabGridBinding,
            override val favicon: ImageView,
            val tabPreview: ImageView,
            override val title: TextView,
            override val close: ImageView,
            override val tabUnread: ImageView,
            override val cardContentsContainer: ViewGroup,
        ) : TabViewHolder(binding.root, favicon, title, close, tabUnread, cardContentsContainer)

        data class ListTabViewHolder(
            val binding: ItemTabListBinding,
            override val favicon: ImageView,
            override val title: TextView,
            val url: TextView,
            override val close: ImageView,
            override val tabUnread: ImageView,
            override val cardContentsContainer: ViewGroup,
        ) : TabViewHolder(binding.root, favicon, title, close, tabUnread, cardContentsContainer)
    }
}
