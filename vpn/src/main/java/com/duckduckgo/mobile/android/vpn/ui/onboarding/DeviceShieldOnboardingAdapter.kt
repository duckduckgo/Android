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
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieCompositionFactory
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.vpn.databinding.ActivityVpnOnboardingPageBinding

class DeviceShieldOnboardingAdapter(
    private val pages: List<VpnOnboardingViewModel.OnboardingPage>,
    private val clickListener: () -> Unit
) : RecyclerView.Adapter<PageViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PageViewHolder {
        val binding = ActivityVpnOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageViewHolder(binding)
    }

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int
    ) {
        holder.bind(pages[position], position, clickListener)
    }
}

class PageViewHolder(val binding: ActivityVpnOnboardingPageBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(
        page: VpnOnboardingViewModel.OnboardingPage,
        position: Int,
        clickListener: () -> Unit
    ) {
        binding.onboardingPageTitle.setText(page.title)
        binding.onboardingPageText.setText(page.text)

        when (position) {
            0 -> {
                showAnimationView(page.imageHeader)
            }
            1 -> {
                showAnimationView(page.imageHeader)
            }
            2 -> {
                showHeaderView(page.imageHeader)
                binding.onboardingPageText.addClickableLink("learn_more_link", binding.onboardingPageText.context.getText(page.text)) {
                    clickListener()
                }
            }
            3 -> {
                showAnimationView(page.imageHeader)
            }
        }
    }

    private fun showAnimationView(animation: Int) {
        binding.onboardingPageAnimation.show()
        binding.onboardingPageImage.gone()

        LottieCompositionFactory.fromRawRes(itemView.context, animation)
        binding.onboardingPageAnimation.setAnimation(animation)
        binding.onboardingPageAnimation.playAnimation()
    }

    private fun showHeaderView(image: Int) {
        binding.onboardingPageAnimation.gone()
        binding.onboardingPageImage.show()

        binding.onboardingPageImage.setImageResource(image)
    }
}
