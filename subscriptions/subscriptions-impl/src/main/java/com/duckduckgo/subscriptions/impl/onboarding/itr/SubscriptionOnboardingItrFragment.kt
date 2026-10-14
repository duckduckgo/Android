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

package com.duckduckgo.subscriptions.impl.onboarding.itr

import android.Manifest
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.FragmentSubscriptionOnboardingItrBinding
import com.duckduckgo.subscriptions.impl.onboarding.features.OnboardingFeature
import com.duckduckgo.subscriptions.impl.onboarding.features.SummaryOfBenefitsFooterView
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SubscriptionOnboardingItrFragment : DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_itr) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentSubscriptionOnboardingItrBinding by viewBinding()

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SubscriptionOnboardingItrViewModel::class.java]
    }

    private val summaryOfBenefitsFooter: SummaryOfBenefitsFooterView
        get() = binding.subscriptionOnboardingItrContent.findViewById(R.id.subscriptionOnboardingFeatureInfoLegalFooter)

    private val writeStoragePermission = registerForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            summaryOfBenefitsFooter.onWriteStoragePermissionGranted()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val feature = OnboardingFeature.ITR
        binding.subscriptionOnboardingItrIcon.setImageResource(feature.iconRes)
        binding.subscriptionOnboardingItrTitle.setText(feature.titleRes)
        binding.subscriptionOnboardingItrDescription.setText(feature.descriptionRes)
        layoutInflater.inflate(feature.contentRes, binding.subscriptionOnboardingItrContent, true)

        summaryOfBenefitsFooter.onWriteStoragePermissionRequired = {
            writeStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        binding.subscriptionOnboardingItrActivateButton.setOnClickListener {
            viewModel.onPrimaryCtaClicked()
        }

        setupScrollFade()
    }

    private fun setupScrollFade() {
        val surfaceColor = requireContext().getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface)
        binding.subscriptionOnboardingItrScrollFade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(ColorUtils.setAlphaComponent(surfaceColor, 0), surfaceColor),
        )

        binding.subscriptionOnboardingItrScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> updateScrollFade() },
        )

        binding.subscriptionOnboardingItrScrollView.doOnLayout { updateScrollFade() }
    }

    private fun updateScrollFade() {
        binding.subscriptionOnboardingItrScrollFade.isVisible =
            binding.subscriptionOnboardingItrScrollView.canScrollVertically(1)
    }
}
