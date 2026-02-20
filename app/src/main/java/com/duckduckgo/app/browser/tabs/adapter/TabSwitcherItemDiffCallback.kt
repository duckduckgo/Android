/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser.tabs.adapter

import android.os.Bundle
import androidx.recyclerview.widget.DiffUtil
import com.duckduckgo.app.tabs.ui.TabSwitcherItem
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.SelectableTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackersAnimationInfoPanel.Companion.ANIMATED_TILE_DEFAULT_ALPHA
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.TrackersAnimationInfoPanel.Companion.ANIMATED_TILE_NO_REPLACE_ALPHA

class TabSwitcherItemDiffCallback(
    private val isDragging: () -> Boolean,
) : DiffUtil.ItemCallback<TabSwitcherItem>() {

    override fun areItemsTheSame(
        oldItem: TabSwitcherItem,
        newItem: TabSwitcherItem,
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: TabSwitcherItem,
        newItem: TabSwitcherItem,
    ): Boolean {
        return when {
            oldItem is TabSwitcherItem.Tab && newItem is TabSwitcherItem.Tab -> {
                oldItem.tabEntity.tabPreviewFile == newItem.tabEntity.tabPreviewFile &&
                    oldItem.tabEntity.viewed == newItem.tabEntity.viewed &&
                    oldItem.tabEntity.title == newItem.tabEntity.title &&
                    oldItem.tabEntity.url == newItem.tabEntity.url &&
                    (oldItem as? SelectableTab)?.isSelected == (newItem as? SelectableTab)?.isSelected
            }
            else -> false
        }
    }

    override fun getChangePayload(
        oldItem: TabSwitcherItem,
        newItem: TabSwitcherItem,
    ): Any {
        val diffBundle = Bundle()

        when {
            oldItem is TabSwitcherItem.Tab && newItem is TabSwitcherItem.Tab -> {
                if (oldItem.tabEntity.title != newItem.tabEntity.title) {
                    diffBundle.putString(DIFF_KEY_TITLE, newItem.tabEntity.title)
                }

                if (oldItem.tabEntity.url != newItem.tabEntity.url) {
                    diffBundle.putString(DIFF_KEY_URL, newItem.tabEntity.url)
                }

                if (oldItem.tabEntity.viewed != newItem.tabEntity.viewed) {
                    diffBundle.putBoolean(DIFF_KEY_VIEWED, newItem.tabEntity.viewed)
                }

                if (oldItem.tabEntity.tabPreviewFile != newItem.tabEntity.tabPreviewFile) {
                    diffBundle.putString(DIFF_KEY_PREVIEW, newItem.tabEntity.tabPreviewFile)
                }

                if ((oldItem as? SelectableTab)?.isSelected != (newItem as? SelectableTab)?.isSelected) {
                    diffBundle.putString(DIFF_KEY_SELECTION, null)
                }
            }
            oldItem is TabSwitcherItem.TrackersAnimationInfoPanel && newItem is TabSwitcherItem.TrackersAnimationInfoPanel -> {
                diffBundle.putFloat(
                    DIFF_ALPHA,
                    if (isDragging()) ANIMATED_TILE_NO_REPLACE_ALPHA else ANIMATED_TILE_DEFAULT_ALPHA,
                )
            }
        }

        return diffBundle
    }

    companion object {
        const val DIFF_KEY_TITLE = "title"
        const val DIFF_KEY_URL = "url"
        const val DIFF_KEY_PREVIEW = "previewImage"
        const val DIFF_KEY_VIEWED = "viewed"
        const val DIFF_ALPHA = "alpha"
        const val DIFF_KEY_SELECTION = "selection"
    }
}
