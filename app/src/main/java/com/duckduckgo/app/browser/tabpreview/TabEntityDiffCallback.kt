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


class TabEntityDiffCallback : DiffUtil.ItemCallback<TabEntity>() {

    override fun areItemsTheSame(oldItem: TabEntity, newItem: TabEntity): Boolean {
        return oldItem.tabId == newItem.tabId
    }

    override fun areContentsTheSame(oldItem: TabEntity, newItem: TabEntity): Boolean {
        return oldItem.tabPreviewFile == newItem.tabPreviewFile &&
                oldItem.viewed == newItem.viewed &&
                oldItem.title == newItem.title
    }

    override fun getChangePayload(oldItem: TabEntity, newItem: TabEntity): Bundle {

        val diffBundle = Bundle()

        if (oldItem.title != newItem.title) {
            diffBundle.putString(DIFF_KEY_TITLE, newItem.title)
        }

        if (oldItem.viewed != newItem.viewed) {
            diffBundle.putBoolean(DIFF_KEY_VIEWED, newItem.viewed)
        }

        if (oldItem.tabPreviewFile != newItem.tabPreviewFile) {
            diffBundle.putString(DIFF_KEY_PREVIEW, newItem.tabPreviewFile)
        }

        return diffBundle
    }

    companion object {
        const val DIFF_KEY_TITLE = "title"
        const val DIFF_KEY_PREVIEW = "previewImage"
        const val DIFF_KEY_VIEWED = "viewed"
    }
}