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

package com.duckduckgo.examplefeature.internal.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.featuretoggles.internal.databinding.ItemFeatureToggleBinding

class FeatureToggleAdapter(
    private val onToggleChanged: (FeatureToggleItem, Boolean) -> Unit,
    private val onItemClicked: (FeatureToggleItem) -> Unit,
) : ListAdapter<FeatureToggleItem, FeatureToggleAdapter.ViewHolder>(FeatureToggleDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).featureKey.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemFeatureToggleBinding.inflate(inflater, parent, false)
        return ViewHolder(binding, onToggleChanged, onItemClicked)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemFeatureToggleBinding,
        private val onToggleChanged: (FeatureToggleItem, Boolean) -> Unit,
        private val onItemClicked: (FeatureToggleItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: FeatureToggleItem? = null

        // Single listener instance reused across binds
        private val switchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            currentItem?.let { onToggleChanged(it, isChecked) }
        }

        init {
            binding.root.showSwitch()
            binding.root.setOnClickListener {
                currentItem?.let { onItemClicked(it) }
            }
        }

        fun bind(item: FeatureToggleItem) {
            currentItem = item
            val listItem = binding.root
            if (item.isSubFeature) {
                listItem.setPrimaryText("\u2514   ${item.displayName}")
                listItem.setPrimaryTextTruncation(truncated = true)
            } else {
                listItem.setPrimaryText(item.displayName)
                listItem.setPrimaryTextTruncation(truncated = false)
            }
            listItem.quietlySetIsChecked(item.isEnabled, switchListener)
        }

        fun setSwitchState(isEnabled: Boolean) {
            binding.root.quietlySetIsChecked(isEnabled, switchListener)
        }
    }

    private class FeatureToggleDiffCallback : DiffUtil.ItemCallback<FeatureToggleItem>() {
        override fun areItemsTheSame(oldItem: FeatureToggleItem, newItem: FeatureToggleItem): Boolean {
            return oldItem.featureKey == newItem.featureKey
        }

        override fun areContentsTheSame(oldItem: FeatureToggleItem, newItem: FeatureToggleItem): Boolean {
            return oldItem.displayName == newItem.displayName &&
                oldItem.isSubFeature == newItem.isSubFeature &&
                oldItem.isEnabled == newItem.isEnabled
        }
    }
}
