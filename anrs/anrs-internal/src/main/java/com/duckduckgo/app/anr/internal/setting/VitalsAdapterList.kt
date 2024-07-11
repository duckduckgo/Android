/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.anr.internal.setting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.anr.internal.databinding.ItemAnrBinding
import com.duckduckgo.app.anr.internal.databinding.ItemCrashBinding
import com.duckduckgo.app.anr.internal.setting.VitalsAdapterList.VitalsItems.AnrItem
import com.duckduckgo.app.anr.internal.setting.VitalsAdapterList.VitalsItems.CrashItem

class VitalsAdapterList : Adapter<ViewHolder>() {

    private var listItems = listOf<VitalsItems>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_ANR -> {
                val binding = ItemAnrBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ANRViewHolder(binding)
            }

            ITEM_VIEW_TYPE_CRASH -> {
                val binding = ItemCrashBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                CrashViewHolder(binding)
            }

            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (listItems[position]) {
            is AnrItem -> ITEM_VIEW_TYPE_ANR
            is CrashItem -> ITEM_VIEW_TYPE_CRASH
        }
    }

    override fun getItemCount(): Int {
        return listItems.size
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is ANRViewHolder -> {
                val item = listItems[position] as AnrItem
                populateANRItem(item, holder)
            }

            is CrashViewHolder -> {
                val item = listItems[position] as CrashItem
                populateCrashItem(item, holder)
            }
        }
    }

    fun setItems(items: List<VitalsItems>) {
        listItems = items
        notifyDataSetChanged()
    }

    private fun populateANRItem(
        anr: AnrItem,
        viewHolder: ANRViewHolder,
    ) {
        with(viewHolder) {
            binding.anrItem.setPrimaryText("ANR at ${anr.timestamp} in custom tab: ${anr.customTab}")
            binding.anrItem.setSecondaryText(anr.stackTrace.take(500))
        }
    }

    private fun populateCrashItem(
        crash: CrashItem,
        viewHolder: CrashViewHolder,
    ) {
        with(viewHolder) {
            binding.crashItem.setPrimaryText("Crash at ${crash.timestamp} in process: ${crash.processName} in custom tab: ${crash.customTab}")
            binding.crashItem.setSecondaryText(crash.stackTrace.take(500))
        }
    }

    sealed class VitalsItems(open val timestamp: String) {
        data class AnrItem(
            val stackTrace: String,
            val customTab: Boolean,
            override val timestamp: String,
        ) : VitalsItems(timestamp)

        data class CrashItem(
            val stackTrace: String,
            val processName: String,
            val customTab: Boolean,
            override val timestamp: String,
        ) : VitalsItems(timestamp)
    }

    open class ANRViewHolder(open val binding: ItemAnrBinding) : RecyclerView.ViewHolder(binding.root)
    open class CrashViewHolder(open val binding: ItemCrashBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val ITEM_VIEW_TYPE_ANR = 0
        private const val ITEM_VIEW_TYPE_CRASH = 1
    }
}
