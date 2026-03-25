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
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ItemDuckAiTabGridBinding
import com.duckduckgo.app.browser.databinding.ItemTabGridBinding
import com.duckduckgo.app.browser.databinding.ItemTabListBinding
import com.duckduckgo.app.browser.databinding.ItemTabSwitcherAnimationInfoPanelBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_ALPHA
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_PREVIEW
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_SELECTION
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_TITLE
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_URL
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_VIEWED
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.model.isAboutBlank
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.DUCK_AI_GRID
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.DUCK_AI_LIST
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.GRID_TAB
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.LIST_TAB
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.SELECTABLE_DUCK_AI_GRID
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.SELECTABLE_DUCK_AI_LIST
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.TRACKER_ANIMATION_TILE_INFO_PANEL
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.TabViewHolder
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.DuckAiTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.SelectableTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackersAnimationInfoPanel.Companion.ANIMATED_TILE_DEFAULT_ALPHA
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackersAnimationInfoPanel.Companion.ANIMATED_TILE_NO_REPLACE_ALPHA
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.swap
import com.duckduckgo.app.browser.AddressDisplayFormatter
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.io.File
import java.security.MessageDigest
import com.duckduckgo.mobile.android.R as AndroidR
import com.duckduckgo.mobile.android.R as CommonR

class TabSwitcherAdapter(
    private val itemClickListener: TabSwitcherListener,
    private val webViewPreviewPersister: WebViewPreviewPersister,
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val dispatchers: DispatcherProvider,
    private val trackerCountAnimator: TrackerCountAnimator,
    private val duckChat: DuckChat,
    private val addressDisplayFormatter: AddressDisplayFormatter,
) : Adapter<ViewHolder>() {

    @Volatile
    private var isDragging: Boolean = false
    @Volatile
    var isFullUrlEnabled: Boolean = true
    private var layoutType: LayoutType = GRID
    private var onAnimationTileCloseClickListener: (() -> Unit)? = null

    private val differ = AsyncListDiffer(this, TabSwitcherItemDiffCallback(isDragging = { isDragging }))

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return differ.currentList[position].id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            GRID_TAB -> {
                val binding = ItemTabGridBinding.inflate(inflater, parent, false)
                addExtraCloseButtonTouchArea(binding.close)
                TabSwitcherViewHolder.GridTabViewHolder(binding)
            }
            LIST_TAB -> {
                val binding = ItemTabListBinding.inflate(inflater, parent, false)
                addExtraCloseButtonTouchArea(binding.close)
                TabSwitcherViewHolder.ListTabViewHolder(binding)
            }
            DUCK_AI_GRID, SELECTABLE_DUCK_AI_GRID -> {
                val binding = ItemDuckAiTabGridBinding.inflate(inflater, parent, false)
                addExtraCloseButtonTouchArea(binding.close)
                TabSwitcherViewHolder.DuckAiTabGridViewHolder(binding)
            }
            DUCK_AI_LIST, SELECTABLE_DUCK_AI_LIST -> {
                val binding = ItemTabListBinding.inflate(inflater, parent, false)
                addExtraCloseButtonTouchArea(binding.close)
                TabSwitcherViewHolder.DuckAiTabListViewHolder(binding)
            }
            TRACKER_ANIMATION_TILE_INFO_PANEL -> {
                val binding = ItemTabSwitcherAnimationInfoPanelBinding.inflate(inflater, parent, false)
                TabSwitcherViewHolder.TrackerAnimationInfoPanelViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int =
        when (val item = differ.currentList[position]) {
            is DuckAiTab -> when (layoutType) {
                GRID -> DUCK_AI_GRID
                LIST -> DUCK_AI_LIST
            }
            is SelectableTab -> {
                val isDuckAi = item.tabEntity.url
                    ?.let { Uri.parse(it) }
                    ?.let { duckChat.isDuckChatUrl(it) } == true
                when {
                    isDuckAi && layoutType == GRID -> SELECTABLE_DUCK_AI_GRID
                    isDuckAi && layoutType == LIST -> SELECTABLE_DUCK_AI_LIST
                    layoutType == GRID -> GRID_TAB
                    else -> LIST_TAB
                }
            }
            is Tab -> when (layoutType) {
                GRID -> GRID_TAB
                LIST -> LIST_TAB
            }
            is TabSwitcherItem.TrackersAnimationInfoPanel -> TRACKER_ANIMATION_TILE_INFO_PANEL
        }

    override fun getItemCount(): Int = differ.currentList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (holder) {
            is TabSwitcherViewHolder.GridTabViewHolder -> {
                bindGridTab(holder, differ.currentList[position] as Tab)
            }
            is TabSwitcherViewHolder.ListTabViewHolder -> {
                bindListTab(holder, differ.currentList[position] as Tab)
            }
            is TabSwitcherViewHolder.DuckAiTabGridViewHolder -> {
                bindDuckAiGridTab(holder, differ.currentList[position] as Tab)
            }
            is TabSwitcherViewHolder.DuckAiTabListViewHolder -> {
                bindDuckAiListTab(holder, differ.currentList[position] as Tab)
            }
            is TabSwitcherViewHolder.TrackerAnimationInfoPanelViewHolder -> {
                val trackersAnimationInfoPanel = differ.currentList[position] as TabSwitcherItem.TrackersAnimationInfoPanel

                val stringRes = if (trackersAnimationInfoPanel.trackerCount == 1) {
                    R.string.trackerBlockedInTheLast7days
                } else {
                    R.string.trackersBlockedInTheLast7days
                }

                trackerCountAnimator.animateTrackersBlockedCountView(
                    context = holder.binding.root.context,
                    stringRes = stringRes,
                    totalTrackerCount = trackersAnimationInfoPanel.trackerCount,
                    trackerTextView = holder.binding.infoPanelText,
                )
                holder.binding.root.setOnClickListener {
                    onAnimationTileCloseClickListener?.invoke()
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

    private fun bindListTab(holder: TabSwitcherViewHolder.ListTabViewHolder, tab: Tab) {
        holder.cancelLoadJobs()
        val context = holder.rootView.context
        val glide = Glide.with(context)
        holder.title.text = extractTabTitle(tab.tabEntity, context)
        holder.url.text = formatUrl(tab.tabEntity.url)
        holder.url.visibility = if (tab.tabEntity.url.isNullOrEmpty()) View.GONE else View.VISIBLE
        updateUnreadIndicator(holder, tab.tabEntity)
        loadFavicon(tab.tabEntity, glide, holder.favicon, holder)
        loadSelectionState(holder, tab)
        attachTabClickListeners(
            tabViewHolder = holder,
            bindingAdapterPosition = { holder.bindingAdapterPosition },
            tabId = tab.id,
        )
    }

    private fun bindGridTab(holder: TabSwitcherViewHolder.GridTabViewHolder, tab: Tab) {
        holder.cancelLoadJobs()
        val context = holder.rootView.context
        val glide = Glide.with(context)
        holder.title.text = extractTabTitle(tab.tabEntity, context)
        updateUnreadIndicator(holder, tab.tabEntity)
        loadFavicon(tab.tabEntity, glide, holder.favicon, holder)
        loadTabPreviewImage(tab.tabEntity, glide, holder.tabPreview, holder)
        loadSelectionState(holder, tab)
        attachTabClickListeners(
            tabViewHolder = holder,
            bindingAdapterPosition = { holder.bindingAdapterPosition },
            tabId = tab.id,
        )
    }

    private fun bindDuckAiGridTab(holder: TabSwitcherViewHolder.DuckAiTabGridViewHolder, tab: Tab) {
        holder.cancelLoadJobs()
        val context = holder.rootView.context
        val glide = Glide.with(context)
        holder.title.text = extractTabTitle(tab.tabEntity, context)
        holder.favicon.setImageResource(CommonR.drawable.ic_duck_ai_color_24)
        updateUnreadIndicator(holder, tab.tabEntity)
        loadTabPreviewImage(tab.tabEntity, glide, holder.tabPreview, holder)
        loadSelectionState(holder, tab)
        attachTabClickListeners(
            tabViewHolder = holder,
            bindingAdapterPosition = { holder.bindingAdapterPosition },
            tabId = tab.id,
        )
    }

    private fun bindDuckAiListTab(holder: TabSwitcherViewHolder.DuckAiTabListViewHolder, tab: Tab) {
        holder.cancelLoadJobs()
        val context = holder.rootView.context
        holder.title.text = context.getString(R.string.duck_ai_tab_label)
        holder.url.text = tab.tabEntity.title ?: ""
        holder.url.visibility = View.VISIBLE
        holder.favicon.setImageResource(CommonR.drawable.ic_duck_ai_color_24)
        updateUnreadIndicator(holder, tab.tabEntity)
        loadSelectionState(holder, tab)
        attachTabClickListeners(
            tabViewHolder = holder,
            bindingAdapterPosition = { holder.bindingAdapterPosition },
            tabId = tab.id,
        )
    }

    private fun attachTabClickListeners(tabViewHolder: TabViewHolder, bindingAdapterPosition: () -> Int, tabId: String) {
        tabViewHolder.rootView.setOnClickListener {
            if (!isDragging) {
                itemClickListener.onTabSelected(tabId)
            }
        }
        tabViewHolder.selectionIndicator.setOnClickListener {
            if (!isDragging) {
                itemClickListener.onTabSelected(tabId)
            }
        }
        tabViewHolder.close.setOnClickListener(
            createCloseClickListener(
                bindingAdapterPosition = bindingAdapterPosition,
                tabSwitcherListener = itemClickListener,
            ),
        )
    }

    private fun loadSelectionState(holder: TabViewHolder, tab: Tab) {
        when (tab) {
            is SelectableTab -> {
                if (tab.isSelected) {
                    holder.selectionIndicator.setImageResource(CommonR.drawable.ic_check_blue_24)
                    holder.selectionIndicator.contentDescription = holder.rootView.resources.getString(R.string.tabSelectedIndicator)
                } else {
                    holder.selectionIndicator.setImageResource(CommonR.drawable.ic_shape_circle_24)
                    holder.selectionIndicator.contentDescription = holder.rootView.resources.getString(R.string.tabNotSelectedIndicator)
                }
                holder.selectionIndicator.show()
                holder.close.isClickable = false
                holder.close.gone()
            }
            else -> {
                holder.selectionIndicator.hide()
                holder.close.isClickable = true
                holder.close.show()
            }
        }
    }

    private fun extractTabTitle(tab: TabEntity, context: Context): String {
        var title = tab.displayTitle(context)
        title = title.removeSuffix(DUCKDUCKGO_TITLE_SUFFIX)
        return title
    }

    private fun formatUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        return if (isFullUrlEnabled) url else addressDisplayFormatter.getShortUrl(url)
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
                tab = differ.currentList[position] as Tab,
                payloads = payloads,
            )
            LIST_TAB -> handlePayloadsForListTab(
                viewHolder = holder as TabSwitcherViewHolder.ListTabViewHolder,
                tab = differ.currentList[position] as Tab,
                payloads = payloads,
            )
            DUCK_AI_GRID, SELECTABLE_DUCK_AI_GRID -> handlePayloadsForDuckAiGridTab(
                viewHolder = holder as TabSwitcherViewHolder.DuckAiTabGridViewHolder,
                tab = differ.currentList[position] as Tab,
                payloads = payloads,
            )
            DUCK_AI_LIST, SELECTABLE_DUCK_AI_LIST -> handlePayloadsForDuckAiListTab(
                viewHolder = holder as TabSwitcherViewHolder.DuckAiTabListViewHolder,
                tab = differ.currentList[position] as Tab,
                payloads = payloads,
            )
            TRACKER_ANIMATION_TILE_INFO_PANEL -> {
                for (payload in payloads) {
                    val bundle = payload as Bundle
                    holder.itemView.alpha = bundle.getFloat(DIFF_ALPHA, ANIMATED_TILE_DEFAULT_ALPHA)
                }
            }
        }
    }

    private fun handlePayloadsForGridTab(
        viewHolder: TabSwitcherViewHolder.GridTabViewHolder,
        tab: Tab,
        payloads: MutableList<Any>,
    ) {
        for (payload in payloads) {
            val bundle = payload as Bundle
            for (key in bundle.keySet()) {
                logcat(VERBOSE) { "$key changed - Need an update for ${tab.tabEntity}" }
            }

            if (bundle.containsKey(DIFF_KEY_PREVIEW)) {
                loadTabPreviewImage(tab.tabEntity, Glide.with(viewHolder.rootView), viewHolder.tabPreview, viewHolder)
            }

            bundle.getString(DIFF_KEY_URL)?.let {
                loadFavicon(tab.tabEntity, Glide.with(viewHolder.rootView), viewHolder.favicon, viewHolder)
            }

            bundle.getString(DIFF_KEY_TITLE)?.let {
                viewHolder.title.text = it
            }

            if (bundle.containsKey(DIFF_KEY_SELECTION)) {
                loadSelectionState(viewHolder, tab)
            }

            if (bundle.containsKey(DIFF_KEY_VIEWED)) {
                updateUnreadIndicator(viewHolder, tab.tabEntity)
            }
        }
    }

    private fun handlePayloadsForListTab(
        viewHolder: TabSwitcherViewHolder.ListTabViewHolder,
        tab: Tab,
        payloads: MutableList<Any>,
    ) {
        for (payload in payloads) {
            val bundle = payload as Bundle
            for (key in bundle.keySet()) {
                logcat(VERBOSE) { "$key changed - Need an update for ${tab.tabEntity}" }
            }

            bundle.getString(DIFF_KEY_URL)?.let {
                viewHolder.url.show()
                viewHolder.url.text = formatUrl(it)
                loadFavicon(tab.tabEntity, Glide.with(viewHolder.rootView), viewHolder.favicon, viewHolder)
            }

            bundle.getString(DIFF_KEY_TITLE)?.let {
                viewHolder.title.text = it
            }

            if (bundle.containsKey(DIFF_KEY_SELECTION)) {
                loadSelectionState(viewHolder, tab)
            }

            if (bundle.containsKey(DIFF_KEY_VIEWED)) {
                updateUnreadIndicator(viewHolder, tab.tabEntity)
            }
        }
    }

    private fun handlePayloadsForDuckAiGridTab(
        viewHolder: TabSwitcherViewHolder.DuckAiTabGridViewHolder,
        tab: Tab,
        payloads: MutableList<Any>,
    ) {
        for (payload in payloads) {
            val bundle = payload as Bundle
            for (key in bundle.keySet()) {
                logcat(VERBOSE) { "$key changed - Need an update for ${tab.tabEntity}" }
            }
            if (bundle.containsKey(DIFF_KEY_PREVIEW)) {
                loadTabPreviewImage(tab.tabEntity, Glide.with(viewHolder.rootView), viewHolder.tabPreview, viewHolder)
            }
            bundle.getString(DIFF_KEY_TITLE)?.let {
                viewHolder.title.text = it
            }
            if (bundle.containsKey(DIFF_KEY_SELECTION)) {
                loadSelectionState(viewHolder, tab)
            }
            if (bundle.containsKey(DIFF_KEY_VIEWED)) {
                updateUnreadIndicator(viewHolder, tab.tabEntity)
            }
        }
    }

    private fun handlePayloadsForDuckAiListTab(
        viewHolder: TabSwitcherViewHolder.DuckAiTabListViewHolder,
        tab: Tab,
        payloads: MutableList<Any>,
    ) {
        for (payload in payloads) {
            val bundle = payload as Bundle
            for (key in bundle.keySet()) {
                logcat(VERBOSE) { "$key changed - Need an update for ${tab.tabEntity}" }
            }
            bundle.getString(DIFF_KEY_TITLE)?.let {
                viewHolder.url.text = it
            }
            if (bundle.containsKey(DIFF_KEY_SELECTION)) {
                loadSelectionState(viewHolder, tab)
            }
            if (bundle.containsKey(DIFF_KEY_VIEWED)) {
                updateUnreadIndicator(viewHolder, tab.tabEntity)
            }
        }
    }

    private fun loadFavicon(
        tab: TabEntity,
        glide: RequestManager,
        view: ImageView,
        holder: TabSwitcherViewHolder,
    ) {
        if (tab.isAboutBlank) {
            glide.clear(view)
            glide.load(AndroidR.drawable.ic_globe_24).into(view)
            return
        }
        val url = tab.url
        if (url.isNullOrBlank()) {
            glide.clear(view)
            glide.load(AndroidR.drawable.ic_dax_icon).into(view)
        } else {
            holder.trackJob(
                lifecycleOwner.lifecycleScope.launch {
                    faviconManager.loadToViewFromLocalWithPlaceholder(tab.tabId, url, view)
                },
            )
        }
    }

    private fun loadTabPreviewImage(
        tab: TabEntity,
        glide: RequestManager,
        tabPreview: ImageView,
        holder: TabSwitcherViewHolder,
    ) {
        fun fitAndClipBottom() = object : Transformation<Bitmap> {
            override fun transform(
                context: Context,
                resource: Resource<Bitmap>,
                outWidth: Int,
                outHeight: Int,
            ): Resource<Bitmap> {
                resource.get().height = context.resources.getDimension(CommonR.dimen.gridItemPreviewHeight).toInt()
                return resource
            }

            override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            }
        }

        val previewFile = tab.tabPreviewFile
        if (tab.url.isNullOrBlank() && !tab.isAboutBlank) {
            glide.load(AndroidR.drawable.ic_dax_icon_72)
                .into(tabPreview)
        } else if (previewFile != null) {
            holder.trackJob(
                lifecycleOwner.lifecycleScope.launch {
                    val cachedWebViewPreview = withContext(dispatchers.io()) {
                        File(webViewPreviewPersister.fullPathForFile(tab.tabId, previewFile)).takeIf { it.exists() }
                    }

                    if (cachedWebViewPreview == null) {
                        glide.clear(tabPreview)
                        return@launch
                    }

                    try {
                        glide.load(cachedWebViewPreview)
                            .transition(DrawableTransitionOptions.withCrossFade()).transform(
                                fitAndClipBottom(),
                                RoundedCorners(tabPreview.context.resources.getDimensionPixelSize(CommonR.dimen.smallShapeCornerRadius)),
                            )
                            .into(tabPreview)
                    } catch (e: Exception) {
                        logcat(ERROR) { "Error loading tab preview for ${tab.tabId}: ${e.message}" }
                        glide.load(AndroidR.drawable.ic_dax_icon_72).into(tabPreview)
                    }
                },
            )
        } else {
            glide.clear(tabPreview)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        if (holder is TabSwitcherViewHolder) {
            holder.cancelLoadJobs()
        }

        val glide = Glide.with(holder.itemView.context.applicationContext)
        when (holder) {
            is TabSwitcherViewHolder.GridTabViewHolder -> {
                glide.clear(holder.tabPreview)
                glide.clear(holder.favicon)
            }
            is TabSwitcherViewHolder.ListTabViewHolder -> {
                glide.clear(holder.favicon)
            }
            is TabSwitcherViewHolder.DuckAiTabGridViewHolder -> {
                glide.clear(holder.tabPreview)
            }
            is TabSwitcherViewHolder.DuckAiTabListViewHolder -> {
                // favicon is a static drawable; no need to clear
            }
        }
    }

    private fun updateAnimatedTileAlpha(alpha: Float) {
        val animatedTilePosition = differ.currentList.indexOfFirst { it is TabSwitcherItem.TrackersAnimationInfoPanel }
        if (animatedTilePosition != -1) {
            notifyItemChanged(
                animatedTilePosition,
                Bundle().apply {
                    putFloat(DIFF_ALPHA, alpha)
                },
            )
        }
    }

    fun updateData(updatedList: List<TabSwitcherItem>, onDataUpdated: Runnable? = null) {
        differ.submitList(updatedList, onDataUpdated)
    }

    fun getTabSwitcherItem(position: Int): TabSwitcherItem? = differ.currentList.getOrNull(position)

    fun getAdapterPositionForTab(tabId: String?): Int = differ.currentList.indexOfFirst {
        it is Tab && it.tabEntity.tabId == tabId
    }

    fun onDraggingStarted() {
        isDragging = true
        updateAnimatedTileAlpha(ANIMATED_TILE_NO_REPLACE_ALPHA)
    }

    fun onDraggingFinished() {
        isDragging = false
        updateAnimatedTileAlpha(ANIMATED_TILE_DEFAULT_ALPHA)
    }

    fun onTabMoved(from: Int, to: Int) {
        updateData(differ.currentList.swap(from, to))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onLayoutTypeChanged(layoutType: LayoutType) {
        this.layoutType = layoutType
        notifyDataSetChanged()
    }

    fun setAnimationTileCloseClickListener(onClick: () -> Unit) {
        onAnimationTileCloseClickListener = onClick
    }

    companion object {
        private const val DUCKDUCKGO_TITLE_SUFFIX = "at DuckDuckGo"
    }

    sealed class TabSwitcherViewHolder(rootView: View) : ViewHolder(rootView) {

        private val loadJobs = mutableListOf<Job>()

        fun trackJob(job: Job) {
            loadJobs.removeAll { it.isCompleted }
            loadJobs.add(job)
        }

        fun cancelLoadJobs() {
            loadJobs.forEach { it.cancel() }
            loadJobs.clear()
        }

        companion object {
            const val GRID_TAB = 0
            const val LIST_TAB = 1
            const val TRACKER_ANIMATION_TILE_INFO_PANEL = 2
            const val DUCK_AI_GRID = 3
            const val DUCK_AI_LIST = 4
            const val SELECTABLE_DUCK_AI_GRID = 5
            const val SELECTABLE_DUCK_AI_LIST = 6

            const val EXTRA_CLOSE_BUTTON_TOUCH_AREA = 6 // dp

            const val MAX_TITLE_LENGTH = 50
        }

        interface TabViewHolder {
            val rootView: View
            val favicon: ImageView
            val title: TextView
            val close: ImageView
            val tabUnread: ImageView
            val selectionIndicator: ImageView
        }

        data class GridTabViewHolder(
            override val rootView: View,
            override val favicon: ImageView,
            override val title: TextView,
            override val close: ImageView,
            override val tabUnread: ImageView,
            override val selectionIndicator: ImageView,
            val tabPreview: ImageView,
        ) : TabSwitcherViewHolder(rootView), TabViewHolder {

            constructor(binding: ItemTabGridBinding) : this(
                rootView = binding.root,
                favicon = binding.favicon,
                title = binding.title,
                close = binding.close,
                tabUnread = binding.tabUnread,
                selectionIndicator = binding.selectionIndicator,
                tabPreview = binding.tabPreview,
            )
        }

        data class ListTabViewHolder(
            override val rootView: View,
            override val favicon: ImageView,
            override val title: TextView,
            override val close: ImageView,
            override val tabUnread: ImageView,
            override val selectionIndicator: ImageView,
            val url: TextView,
        ) : TabSwitcherViewHolder(rootView), TabViewHolder {

            constructor(binding: ItemTabListBinding) : this(
                rootView = binding.root,
                favicon = binding.favicon,
                title = binding.title,
                close = binding.close,
                tabUnread = binding.tabUnread,
                selectionIndicator = binding.selectionIndicator,
                url = binding.url,
            )
        }

        data class DuckAiTabGridViewHolder(
            override val rootView: View,
            override val favicon: ImageView,
            override val title: TextView,
            override val close: ImageView,
            override val tabUnread: ImageView,
            override val selectionIndicator: ImageView,
            val tabPreview: ImageView,
        ) : TabSwitcherViewHolder(rootView), TabViewHolder {

            constructor(binding: ItemDuckAiTabGridBinding) : this(
                rootView = binding.root,
                favicon = binding.favicon,
                title = binding.title,
                close = binding.close,
                tabUnread = binding.tabUnread,
                selectionIndicator = binding.selectionIndicator,
                tabPreview = binding.tabPreview,
            )
        }

        data class DuckAiTabListViewHolder(
            override val rootView: View,
            override val favicon: ImageView,
            override val title: TextView,
            override val close: ImageView,
            override val tabUnread: ImageView,
            override val selectionIndicator: ImageView,
            val url: TextView,
        ) : TabSwitcherViewHolder(rootView), TabViewHolder {

            constructor(binding: ItemTabListBinding) : this(
                rootView = binding.root,
                favicon = binding.favicon,
                title = binding.title,
                close = binding.close,
                tabUnread = binding.tabUnread,
                selectionIndicator = binding.selectionIndicator,
                url = binding.url,
            )
        }

        data class TrackerAnimationInfoPanelViewHolder(
            val binding: ItemTabSwitcherAnimationInfoPanelBinding,
        ) : TabSwitcherViewHolder(binding.root)
    }
}

private fun addExtraCloseButtonTouchArea(closeButton: ImageView) {
    val parent = closeButton.parent as View
    parent.post {
        val extraSpace = TabSwitcherAdapter.TabSwitcherViewHolder.Companion.EXTRA_CLOSE_BUTTON_TOUCH_AREA.toPx()
        val touchableArea = Rect()
        closeButton.getHitRect(touchableArea)
        touchableArea.top -= extraSpace
        touchableArea.bottom += extraSpace
        touchableArea.left -= extraSpace
        touchableArea.right += extraSpace
        parent.touchDelegate = TouchDelegate(touchableArea, closeButton)
    }
}
