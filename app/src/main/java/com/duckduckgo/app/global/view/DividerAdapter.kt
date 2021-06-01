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

package com.duckduckgo.app.global.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R

class DividerAdapter : RecyclerView.Adapter<DividerAdapter.DividerViewHolder>() {

    class DividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DividerViewHolder {
        return DividerViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_item_divider, parent, false))
    }

    override fun onBindViewHolder(holder: DividerViewHolder, position: Int) {
        // noop
    }

    override fun getItemCount(): Int = 1
}
