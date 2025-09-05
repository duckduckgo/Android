/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.webtrackingprotection.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.databinding.ItemFeatureGridBinding
import com.duckduckgo.app.webtrackingprotection.list.FeatureGridAdapter.GridItemViewHolder

class FeatureGridAdapter : ListAdapter<FeatureGridItem, GridItemViewHolder>(FeatureGridItemDiffCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): GridItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return GridItemViewHolder(ItemFeatureGridBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(
        holder: GridItemViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class GridItemViewHolder(private val binding: ItemFeatureGridBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FeatureGridItem) {
            binding.icon.setImageResource(item.iconRes)
            binding.title.setText(item.titleRes)
            binding.description.setText(item.descriptionRes)
        }
    }

    private class FeatureGridItemDiffCallback : DiffUtil.ItemCallback<FeatureGridItem>() {

        override fun areItemsTheSame(
            oldItem: FeatureGridItem,
            newItem: FeatureGridItem,
        ): Boolean = oldItem == newItem

        override fun areContentsTheSame(
            oldItem: FeatureGridItem,
            newItem: FeatureGridItem,
        ): Boolean = oldItem == newItem
    }
}

data class FeatureGridItem(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)
