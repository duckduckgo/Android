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

package com.duckduckgo.app.browser.tabpreview

import android.os.Bundle
import androidx.recyclerview.widget.DiffUtil
import com.duckduckgo.app.tabs.model.TabEntity

class TabEntityDiffCallback(old: List<TabEntity>, new: List<TabEntity>) : DiffUtil.Callback() {

    // keep a local copy of the lists to avoid any changes to the lists during the diffing process
    private val oldList = old.toList()
    private val newList = new.toList()

    private fun areItemsTheSame(
        oldItem: TabEntity,
        newItem: TabEntity,
    ): Boolean {
        return oldItem.tabId == newItem.tabId
    }

    private fun areContentsTheSame(
        oldItem: TabEntity,
        newItem: TabEntity,
    ): Boolean {
        return oldItem.tabPreviewFile == newItem.tabPreviewFile &&
            oldItem.viewed == newItem.viewed &&
            oldItem.title == newItem.title &&
            oldItem.url == newItem.url
    }

    private fun getChangePayload(
        oldItem: TabEntity,
        newItem: TabEntity,
    ): Bundle {
        val diffBundle = Bundle()

        if (oldItem.title != newItem.title) {
            diffBundle.putString(DIFF_KEY_TITLE, newItem.title)
        }

        if (oldItem.url != newItem.url) {
            diffBundle.putString(DIFF_KEY_URL, newItem.url)
        }

        if (oldItem.viewed != newItem.viewed) {
            diffBundle.putBoolean(DIFF_KEY_VIEWED, newItem.viewed)
        }

        if (oldItem.tabPreviewFile != newItem.tabPreviewFile) {
            diffBundle.putString(DIFF_KEY_PREVIEW, newItem.tabPreviewFile)
        }

        return diffBundle
    }

    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return if (oldItemPosition in oldList.indices && newItemPosition in newList.indices) {
            areItemsTheSame(oldList[oldItemPosition], newList[newItemPosition])
        } else {
            false
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return if (oldItemPosition in oldList.indices && newItemPosition in newList.indices) {
            areContentsTheSame(oldList[oldItemPosition], newList[newItemPosition])
        } else {
            false
        }
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
        return if (oldItemPosition in oldList.indices && newItemPosition in newList.indices) {
            getChangePayload(oldList[oldItemPosition], newList[newItemPosition])
        } else {
            Bundle()
        }
    }

    companion object {
        const val DIFF_KEY_TITLE = "title"
        const val DIFF_KEY_URL = "url"
        const val DIFF_KEY_PREVIEW = "previewImage"
        const val DIFF_KEY_VIEWED = "viewed"
    }
}
