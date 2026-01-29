/*
 * Copyright (c) 2026 DuckDuckGo
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
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.DialogTabGroupDetailsBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabGroupWithTabs
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.DispatcherProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TabGroupDetailsDialog(
    context: Context,
    tabGroupWithTabs: TabGroupWithTabs,
    private val faviconManager: FaviconManager,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    onTabSelected: (tabId: String) -> Unit,
    private val onTabClosed: (tabId: String) -> Unit,
    private val onTabRemovedFromGroup: (tabId: String) -> Unit,
) {
    private val binding = DialogTabGroupDetailsBinding.inflate(LayoutInflater.from(context))

    private val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .create()

    // Track if we're hovering over the remove zone during drag
    private var isHoveringOverRemoveZone = false

    private val adapter = TabGroupDetailsAdapter(
        tabs = tabGroupWithTabs.tabs.toMutableList(),
        faviconManager = faviconManager,
        webViewPreviewPersister = webViewPreviewPersister,
        coroutineScope = coroutineScope,
        dispatchers = dispatchers,
        onTabClicked = { tabId ->
            dialog.dismiss()
            onTabSelected(tabId)
        },
        onTabClosed = { remainingCount, tabId ->
            onTabClosed(tabId)
            if (remainingCount == 0) {
                dialog.dismiss()
            }
        },
    )

    private val itemTouchHelper = ItemTouchHelper(
        object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    binding.removeFromGroupZone.show()
                    binding.removeFromGroupZone.setBackgroundResource(R.drawable.remove_from_group_zone_background)
                    viewHolder?.itemView?.alpha = 0.7f
                    isHoveringOverRemoveZone = false
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1f

                // Check if we were hovering over the remove zone when dropped
                if (isHoveringOverRemoveZone) {
                    val position = viewHolder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val tabId = adapter.getTabId(position)
                        if (tabId != null) {
                            adapter.removeTab(position)
                            onTabRemovedFromGroup(tabId)

                            if (adapter.itemCount == 0) {
                                dialog.dismiss()
                            }
                        }
                    }
                }

                isHoveringOverRemoveZone = false
                binding.removeFromGroupZone.gone()
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean,
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                    val itemView = viewHolder.itemView
                    val removeZone = binding.removeFromGroupZone

                    // Get recycler view bottom as reference (remove zone is at bottom of dialog)
                    val recyclerLocation = IntArray(2)
                    val itemLocation = IntArray(2)
                    recyclerView.getLocationOnScreen(recyclerLocation)
                    itemView.getLocationOnScreen(itemLocation)

                    // The remove zone starts at the recycler view's bottom
                    val removeZoneTop = recyclerLocation[1] + recyclerView.height

                    // Calculate dragged item bottom with offset
                    val itemBottom = itemLocation[1] + itemView.height + dY

                    // Check if 30% of the card has crossed into the remove zone area
                    val threshold = itemView.height * 0.3f
                    val overlapAmount = itemBottom - removeZoneTop

                    val wasHovering = isHoveringOverRemoveZone
                    isHoveringOverRemoveZone = overlapAmount >= threshold

                    // Update visual state if changed
                    if (wasHovering != isHoveringOverRemoveZone) {
                        if (isHoveringOverRemoveZone) {
                            removeZone.setBackgroundResource(R.drawable.remove_from_group_zone_background_highlighted)
                        } else {
                            removeZone.setBackgroundResource(R.drawable.remove_from_group_zone_background)
                        }
                    }
                }
            }
        },
    )

    init {
        binding.groupName.text = tabGroupWithTabs.group.name
        binding.tabsList.layoutManager = GridLayoutManager(context, 2)
        binding.tabsList.adapter = adapter
        itemTouchHelper.attachToRecyclerView(binding.tabsList)
    }

    fun show() {
        dialog.show()
    }
}

private class TabGroupDetailsAdapter(
    private val tabs: MutableList<TabEntity>,
    private val faviconManager: FaviconManager,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val onTabClicked: (tabId: String) -> Unit,
    private val onTabClosed: (remainingCount: Int, tabId: String) -> Unit,
) : RecyclerView.Adapter<TabGroupDetailsAdapter.TabViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab_group_detail, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.bind(tab)
    }

    override fun getItemCount(): Int = tabs.size

    fun getTabId(position: Int): String? = tabs.getOrNull(position)?.tabId

    fun removeTab(position: Int) {
        if (position in tabs.indices) {
            tabs.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val favicon: ImageView = itemView.findViewById(R.id.favicon)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val tabPreview: ImageView = itemView.findViewById(R.id.tabPreview)
        private val close: ImageView = itemView.findViewById(R.id.close)

        fun bind(tab: TabEntity) {
            val context = itemView.context
            val glide = Glide.with(context)

            title.text = tab.title ?: tab.url ?: context.getString(R.string.homeTab)

            // Load favicon
            val url = tab.url
            if (!url.isNullOrBlank()) {
                coroutineScope.launch {
                    faviconManager.loadToViewFromLocalWithPlaceholder(tab.tabId, url, favicon)
                }
            }

            // Load preview
            val previewFile = tab.tabPreviewFile
            if (previewFile != null) {
                coroutineScope.launch {
                    val file = withContext(dispatchers.io()) {
                        File(webViewPreviewPersister.fullPathForFile(tab.tabId, previewFile)).takeIf { it.exists() }
                    }
                    if (file != null) {
                        glide.load(file)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .into(tabPreview)
                    } else {
                        glide.clear(tabPreview)
                    }
                }
            } else {
                glide.clear(tabPreview)
            }

            itemView.setOnClickListener {
                onTabClicked(tab.tabId)
            }

            close.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    removeTab(position)
                    onTabClosed(tabs.size, tab.tabId)
                }
            }
        }
    }
}
