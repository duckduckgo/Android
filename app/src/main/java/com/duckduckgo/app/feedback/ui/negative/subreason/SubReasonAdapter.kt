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

package com.duckduckgo.app.feedback.ui.negative.subreason

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.ui.negative.FeedbackTypeDisplay.FeedbackTypeSubReasonDisplay
import kotlinx.android.synthetic.main.item_feedback_reason.view.*


class SubReasonAdapter(private val itemClickListener: (FeedbackTypeSubReasonDisplay) -> Unit) :
    ListAdapter<FeedbackTypeSubReasonDisplay, SubReasonAdapter.ViewHolder>(DiffCallback()) {

    class DiffCallback : DiffUtil.ItemCallback<FeedbackTypeSubReasonDisplay>() {
        override fun areItemsTheSame(oldItem: FeedbackTypeSubReasonDisplay, newItem: FeedbackTypeSubReasonDisplay): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: FeedbackTypeSubReasonDisplay, newItem: FeedbackTypeSubReasonDisplay): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val root = inflater.inflate(R.layout.item_feedback_reason, parent, false)
        return ViewHolder(root, root.reason)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), itemClickListener)
    }

    data class ViewHolder(val root: View, val reasonTextView: TextView) : RecyclerView.ViewHolder(root) {

        fun bind(reason: FeedbackTypeSubReasonDisplay, clickListener: (FeedbackTypeSubReasonDisplay) -> Unit) {
            reasonTextView.text = root.context.getString(reason.listDisplayResId)
            itemView.setOnClickListener { clickListener(reason) }
        }
    }

}