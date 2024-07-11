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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.duckduckgo.app.browser.databinding.ItemTabBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.TabEntityDiffCallback
import com.duckduckgo.app.browser.tabpreview.TabEntityDiffCallback.Companion.DIFF_KEY_PREVIEW
import com.duckduckgo.app.browser.tabpreview.TabEntityDiffCallback.Companion.DIFF_KEY_TITLE
import com.duckduckgo.app.browser.tabpreview.TabEntityDiffCallback.Companion.DIFF_KEY_VIEWED
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabViewHolder
import com.duckduckgo.common.ui.view.show
import java.io.File
import kotlinx.coroutines.launch
import timber.log.Timber

class TabSwitcherAdapter(
    private val itemClickListener: TabSwitcherListener,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
) :
    ListAdapter<TabEntity, TabViewHolder>(TabEntityDiffCallback()) {

    private var isDragging: Boolean = false

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): TabViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTabBinding.inflate(inflater, parent, false)

        return TabViewHolder(
            binding = binding,
            favicon = binding.favicon,
            tabPreview = binding.tabPreview,
            title = binding.title,
            close = binding.close,
            cardContentsContainer = binding.cardContentsContainer,
            tabUnread = binding.tabUnread,
        )
    }

    override fun onBindViewHolder(
        holder: TabViewHolder,
        position: Int,
    ) {
        val context = holder.binding.root.context
        val tab = getItem(position)
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

        val tab = getItem(position)

        for (payload in payloads) {
            val bundle = payload as Bundle

            for (key: String in bundle.keySet()) {
                Timber.v("$key changed - Need an update for $tab")
            }

            bundle[DIFF_KEY_PREVIEW]?.let {
                loadTabPreviewImage(tab, Glide.with(holder.binding.root), holder)
            }

            bundle[DIFF_KEY_TITLE]?.let {
                holder.title.text = it as String
            }

            bundle[DIFF_KEY_VIEWED]?.let {
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
        holder: TabViewHolder,
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
            .placeholder(holder.tabPreview.drawable)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.tabPreview)

        holder.tabPreview.show()
    }

    private fun attachClickListeners(
        holder: TabViewHolder,
        tab: TabEntity,
    ) {
        holder.binding.root.setOnClickListener {
            if (!isDragging) {
                itemClickListener.onTabSelected(tab)
            }
        }
        holder.close.setOnClickListener {
            itemClickListener.onTabDeleted(holder.bindingAdapterPosition, false)
        }
    }

    fun updateData(data: List<TabEntity>?) {
        if (data == null) return
        submitList(data)
    }

    fun getTab(position: Int): TabEntity = getItem(position)

    fun adapterPositionForTab(tabId: String?): Int {
        if (tabId == null) return -1
        return currentList.indexOfFirst { it.tabId == tabId }
    }

    fun onDraggingStarted() {
        isDragging = true
    }

    fun onDraggingFinished() {
        isDragging = false
    }

    companion object {
        private const val DUCKDUCKGO_TITLE_SUFFIX = "at DuckDuckGo"
    }

    data class TabViewHolder(
        val binding: ItemTabBinding,
        val favicon: ImageView,
        val tabPreview: ImageView,
        val title: TextView,
        val close: ImageView,
        val tabUnread: ImageView,
        val cardContentsContainer: ViewGroup,
    ) : ViewHolder(binding.root)
}
