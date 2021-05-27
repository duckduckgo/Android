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

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.vpn.R

class DeviceShieldOnboardingAdapter(val pages: List<DeviceShieldOnboardingViewModel.OnboardingPage>) : RecyclerView.Adapter<PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageViewHolder(parent)

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position], position)
    }
}

class PageViewHolder(parent: ViewGroup) :
    RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.activity_device_shield_onboarding_page, parent, false)
    ) {
    private val pageTitle: TextView = itemView.findViewById(R.id.onboarding_page_title)
    private val pageText: TextView = itemView.findViewById(R.id.onboarding_page_text)

    fun bind(page: DeviceShieldOnboardingViewModel.OnboardingPage, position: Int) {
        pageTitle.setText(page.title)
        pageText.setText(page.text)
    }
}
