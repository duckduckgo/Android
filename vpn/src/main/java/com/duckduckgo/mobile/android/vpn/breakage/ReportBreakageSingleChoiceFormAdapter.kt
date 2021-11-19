/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.breakage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.vpn.R

class ReportBreakageSingleChoiceFormAdapter(private val listener: Listener) : RecyclerView.Adapter<ReportBreakageSingleChoiceFormViewHolder>() {

    private val choices: MutableList<Choice> = mutableListOf()

    fun update(updatedData: List<Choice>) {
        val oldList = choices
        val diffResult = DiffUtil.calculateDiff(DiffCallback(oldList, updatedData))
        choices.clear()
        choices.addAll(updatedData)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportBreakageSingleChoiceFormViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.view_report_breakage_text_single_choice_entry, parent, false)
        return ReportBreakageSingleChoiceFormViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportBreakageSingleChoiceFormViewHolder, position: Int) {
        holder.bind(choices[position], position, listener)
    }

    override fun getItemCount(): Int {
        return choices.size
    }

    override fun getItemId(position: Int): Long {
        return choices[position].questionStringRes.toLong()
    }

    private class DiffCallback(
        private val oldList: List<Choice>,
        private val newList: List<Choice>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].questionStringRes == newList[newItemPosition].questionStringRes
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }

    }

    interface Listener {
        fun onChoiceSelected(choice: Choice, position: Int)
    }
}

class ReportBreakageSingleChoiceFormViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(choice: Choice, position: Int, listener: ReportBreakageSingleChoiceFormAdapter.Listener) {
        with(itemView.findViewById<TextView>(R.id.single_choice_text)) {
            text = itemView.context.getString(choice.questionStringRes)
            setOnClickListener {
                listener.onChoiceSelected(choice, position)
            }
        }
        itemView.findViewById<RadioButton>(R.id.single_choice_selector).quietlySetIsChecked(choice.isSelected) { _, _ ->
            listener.onChoiceSelected(choice, position)
        }
    }
}
