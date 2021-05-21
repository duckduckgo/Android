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
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.vpn.R

class DeviceShieldOnboardingAdapter() : RecyclerView.Adapter<PageViewHolder>() {

    data class OnboardingPage(val imageHeader: Int, val title: Int, val text: Int)

    private val pages = listOf(
        OnboardingPage(
            R.drawable.device_shield_onboarding_page_one_header,
            R.string.deviceShieldOnboardingLastPageOneTitle, R.string.deviceShieldOnboardingLatsPageOneSubtitle
        ),
        OnboardingPage(
            R.drawable.device_shield_onboarding_page_two_header,
            R.string.deviceShieldOnboardingLastPageTwoTitle, R.string.deviceShieldOnboardingLastPageTwoSubTitle
        ),
        OnboardingPage(
            R.drawable.device_shield_onboarding_page_three_header,
            R.string.deviceShieldOnboardingLastPageThreeTitle, R.string.deviceShieldOnboardingLastPageThreeSubTitle
        )
    )

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
    private val pageHeader: ImageView = itemView.findViewById(R.id.onboarding_page_header)
    private val pageTitle: TextView = itemView.findViewById(R.id.onboarding_page_title)
    private val pageText: TextView = itemView.findViewById(R.id.onboarding_page_text)

    private val indicatorOne: ImageView = itemView.findViewById(R.id.onboarding_active_indicator_one)
    private val indicatorTwo: ImageView = itemView.findViewById(R.id.onboarding_active_indicator_two)
    private val indicatorThree: ImageView = itemView.findViewById(R.id.onboarding_active_indicator_three)
    private val indicators = listOf(indicatorOne, indicatorTwo, indicatorThree)

    fun bind(page: DeviceShieldOnboardingAdapter.OnboardingPage, position: Int) {
        pageHeader.setImageResource(page.imageHeader)
        pageTitle.setText(page.title)
        pageText.setText(page.text)
        indicators[position].setImageResource(R.drawable.ic_active_dot)

    }
}
