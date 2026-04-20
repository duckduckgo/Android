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

package com.duckduckgo.user.website.blocklist.impl.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.user.website.blocklist.impl.R

class UserBlockedSitesAdapter(
    private val onUnblock: (String) -> Unit,
) : ListAdapter<UserBlockedSitesViewModel.Item, UserBlockedSitesAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_item_user_blocked_site, parent, false)
        return VH(view, onUnblock)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onUnblock: (String) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val domainText: TextView = itemView.findViewById(R.id.blockedSiteDomain)
        private val addedText: TextView = itemView.findViewById(R.id.blockedSiteAdded)
        private val removeBtn: ImageButton = itemView.findViewById(R.id.blockedSiteRemove)

        fun bind(item: UserBlockedSitesViewModel.Item) {
            domainText.text = item.domain
            addedText.text = DateUtils.getRelativeTimeSpanString(
                item.addedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            removeBtn.contentDescription = itemView.context.getString(
                R.string.userBlockedSitesUnblockContentDescription,
                item.domain,
            )
            removeBtn.setOnClickListener { onUnblock(item.domain) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserBlockedSitesViewModel.Item>() {
            override fun areItemsTheSame(
                oldItem: UserBlockedSitesViewModel.Item,
                newItem: UserBlockedSitesViewModel.Item,
            ): Boolean = oldItem.domain == newItem.domain

            override fun areContentsTheSame(
                oldItem: UserBlockedSitesViewModel.Item,
                newItem: UserBlockedSitesViewModel.Item,
            ): Boolean = oldItem == newItem
        }
    }
}
