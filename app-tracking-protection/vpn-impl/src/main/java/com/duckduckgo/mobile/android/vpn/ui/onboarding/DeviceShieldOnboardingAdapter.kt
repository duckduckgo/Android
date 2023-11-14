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
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.mobile.android.vpn.R

class DeviceShieldOnboardingAdapter(
    private val pages: List<VpnOnboardingViewModel.OnboardingPage>,
    private val clickListener: () -> Unit,
) : RecyclerView.Adapter<PageViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ) = PageViewHolder(parent)

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int,
    ) {
        holder.bind(pages[position], position, clickListener)
    }
}

class PageViewHolder(parent: ViewGroup) :
    RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.activity_vpn_onboarding_page, parent, false),
    ) {
    private val pageTitle: TextView = itemView.findViewById(R.id.onboarding_page_title)
    private val pageText: TextView = itemView.findViewById(R.id.onboarding_page_text)
    private val onboardingHeader: ImageView = itemView.findViewById(R.id.onboarding_page_image)
    private val onboardingAnimation: LottieAnimationView = itemView.findViewById(R.id.onboarding_page_animation)

    fun bind(
        page: VpnOnboardingViewModel.OnboardingPage,
        position: Int,
        clickListener: () -> Unit,
    ) {
        pageTitle.setText(page.title)
        pageText.setText(page.text)

        when (position) {
            0 -> {
                showAnimationView(page.imageHeader)
            }
            1 -> {
                showAnimationView(page.imageHeader)
            }
            2 -> {
                showHeaderView(page.imageHeader)
                pageText.addClickableLink("learn_more_link", pageText.context.getText(page.text)) {
                    clickListener()
                }
            }
            3 -> {
                showAnimationView(page.imageHeader)
            }
        }
    }

    private fun showAnimationView(animation: Int) {
        onboardingAnimation.show()
        onboardingHeader.gone()

        LottieCompositionFactory.fromRawRes(itemView.context, animation)
        onboardingAnimation.setAnimation(animation)
        onboardingAnimation.playAnimation()
    }

    private fun showHeaderView(image: Int) {
        onboardingAnimation.gone()
        onboardingHeader.show()

        onboardingHeader.setImageResource(image)
    }
}
