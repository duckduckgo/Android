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
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.swap
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class TabSwitcherAdapter(
    private val itemClickListener: TabSwitcherListener,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val dispatchers: DispatcherProvider,
) : Adapter<TabViewHolder>() {

    private val list = mutableListOf<TabEntity>()
    private var isDragging: Boolean = false
    private var layoutType: LayoutType = LayoutType.GRID

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return list[position].tabId.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (layoutType) {
            LayoutType.GRID -> {
                val binding = ItemTabGridBinding.inflate(inflater, parent, false)
                GridTabViewHolder(binding)
            }
            LayoutType.LIST -> {
                val binding = ItemTabListBinding.inflate(inflater, parent, false)
                ListTabViewHolder(binding)
            }
        }
    }

    override fun getItemViewType(position: Int): Int = layoutType.ordinal

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = list[position]
        when (holder) {
            is GridTabViewHolder -> bindGridTab(holder, tab)
            is ListTabViewHolder -> bindListTab(holder, tab)
        }
    }

    private fun bindListTab(holder: ListTabViewHolder, tab: TabEntity) {
        val context = holder.binding.root.context
        holder.title.text = extractTabTitle(tab, context)
        holder.url.text = tab.url ?: ""
        holder.url.visibility = if (tab.url.isNullOrEmpty()) View.GONE else View.VISIBLE
        updateUnreadIndicator(holder, tab)
        loadFavicon(tab, holder.favicon)
        attachClickListeners(holder, tab)
    }

    private fun bindGridTab(holder: GridTabViewHolder, tab: TabEntity) {
        val context = holder.binding.root.context
        val glide = Glide.with(context)
        holder.title.text = extractTabTitle(tab, context)
        updateUnreadIndicator(holder, tab)
        loadFavicon(tab, holder.favicon)
        loadTabPreviewImage(tab, glide, holder)
        attachClickListeners(holder, tab)
    }

    private fun extractTabTitle(tab: TabEntity, context: Context): String {
        var title = tab.displayTitle(context)
        title = title.removeSuffix(DUCKDUCKGO_TITLE_SUFFIX)
        return title
    }

    private fun updateUnreadIndicator(holder: TabViewHolder, tab: TabEntity) {
        holder.tabUnread.visibility = if (tab.viewed) View.INVISIBLE else View.VISIBLE
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }

        val tab = list[position]
        for (payload in payloads) {
            val bundle = payload as Bundle
            for (key in bundle.keySet()) {
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

    private fun loadFavicon(tab: TabEntity, view: ImageView) {
        val url = tab.url ?: return
        lifecycleOwner.lifecycleScope.launch {
            faviconManager.loadToViewFromLocalWithPlaceholder(tab.tabId, url, view)
        }
    }

    private fun loadTabPreviewImage(tab: TabEntity, glide: RequestManager, holder: GridTabViewHolder) {
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

    private fun attachClickListeners(holder: TabViewHolder, tab: TabEntity) {
        holder.rootView.setOnClickListener {
            if (!isDragging) {
                itemClickListener.onTabSelected(tab)
            }
        }
        holder.close.setOnClickListener {
            itemClickListener.onTabDeleted(holder.bindingAdapterPosition, false)
        }
    }

    fun updateData(updatedList: List<TabEntity>) {
        val diffResult = DiffUtil.calculateDiff(TabEntityDiffCallback(list, updatedList))
        list.clear()
        list.addAll(updatedList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun getTab(position: Int): TabEntity? = list.getOrNull(position)

    fun adapterPositionForTab(tabId: String?): Int = list.indexOfFirst { it.tabId == tabId }

    fun onDraggingStarted() {
        isDragging = true
    }

    fun onDraggingFinished() {
        isDragging = false
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

    sealed class TabViewHolder(
        val rootView: View,
        val favicon: ImageView,
        val title: TextView,
        val close: ImageView,
        val tabUnread: ImageView,
    ) : ViewHolder(rootView) {
        data class GridTabViewHolder(
            val binding: ItemTabGridBinding,
        ) : TabViewHolder(binding.root, binding.favicon, binding.title, binding.close, binding.tabUnread) {
            val tabPreview: ImageView = binding.tabPreview
        }

        data class ListTabViewHolder(
            val binding: ItemTabListBinding,
        ) : TabViewHolder(binding.root, binding.favicon, binding.title, binding.close, binding.tabUnread) {
            val url: TextView = binding.url
        }
    }
}
