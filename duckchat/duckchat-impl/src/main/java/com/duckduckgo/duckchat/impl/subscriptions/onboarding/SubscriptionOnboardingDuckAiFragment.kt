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

package com.duckduckgo.duckchat.impl.subscriptions.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentSubscriptionOnboardingDuckAiBinding
import com.duckduckgo.duckchat.impl.databinding.ViewSubscriptionOnboardingDuckAiModelBinding
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.duckchat.impl.subscriptions.onboarding.SubscriptionOnboardingDuckAiViewModel.ModelItem
import com.duckduckgo.duckchat.impl.subscriptions.onboarding.SubscriptionOnboardingDuckAiViewModel.ViewState
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingFeature
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionOnboardingFeatureInfoScreen
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Duck.ai step of the native subscription onboarding. Shows the available Duck.ai models with their tier, lets
 * the user pick one, and either starts Duck.ai with that model or skips the step. When AI features are not
 * available it falls back to a placeholder screen (to be designed later).
 */
@InjectWith(FragmentScope::class)
class SubscriptionOnboardingDuckAiFragment : DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_duck_ai) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: FragmentSubscriptionOnboardingDuckAiBinding by viewBinding()

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SubscriptionOnboardingDuckAiViewModel::class.java]
    }

    private var aiEnabled: Boolean? = null
    private var renderedModelIds: List<String>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.subscriptionOnboardingDuckAiStartButton.setOnClickListener {
            when (aiEnabled) {
                true -> viewModel.onStartClicked()
                false -> viewModel.onPlaceholderPrimaryClicked()
                null -> {} // wait until AI availability is known
            }
        }
        binding.subscriptionOnboardingDuckAiNotNowButton.setOnClickListener {
            viewModel.onNotNowClicked()
        }
        observeViewState()
    }

    private fun observeViewState() {
        viewModel.viewState()
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: ViewState) = with(binding) {
        val enabled = state.aiEnabled ?: return@with
        aiEnabled = enabled
        if (!enabled) {
            subscriptionOnboardingDuckAiHeaderText.gone()
            subscriptionOnboardingDuckAiModelList.gone()
            subscriptionOnboardingDuckAiPlaceholder.show()
            subscriptionOnboardingDuckAiNotNowButton.gone()
            subscriptionOnboardingDuckAiStartButton.setText(R.string.subscriptionOnboardingDuckAiNext)
            return@with
        }
        subscriptionOnboardingDuckAiPlaceholder.gone()
        subscriptionOnboardingDuckAiHeaderText.show()
        subscriptionOnboardingDuckAiModelList.show()
        subscriptionOnboardingDuckAiNotNowButton.show()
        subscriptionOnboardingDuckAiStartButton.setText(R.string.subscriptionOnboardingDuckAiStart)
        setHeaderTextWithLearnMore()
        renderModels(state.models, state.selectedModelId)
    }

    /** Sets the header copy and wires its "Learn More" annotation to open the Duck.ai feature info screen. */
    private fun setHeaderTextWithLearnMore() {
        binding.subscriptionOnboardingDuckAiHeaderText.addClickableSpan(
            getText(R.string.subscriptionOnboardingDuckAiHeaderText),
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        globalActivityStarter.start(
                            requireContext(),
                            SubscriptionOnboardingFeatureInfoScreen(SubscriptionOnboardingFeature.DUCK_AI),
                        )
                    }
                },
            ),
        )
    }

    private fun renderModels(models: List<ModelItem>, selectedId: String?) {
        val container = binding.subscriptionOnboardingDuckAiModelList
        val ids = models.map { it.id }
        if (ids != renderedModelIds) {
            container.removeAllViews()
            val inflater = LayoutInflater.from(container.context)
            models.forEach { model ->
                val row = ViewSubscriptionOnboardingDuckAiModelBinding.inflate(inflater, container, false)
                row.duckAiModelIcon.setImageResource(model.iconRes)
                row.duckAiModelName.text = model.displayName
                val tier = tierLabel(model.tier)
                if (tier != null) {
                    row.duckAiModelTier.text = tier
                    row.duckAiModelTier.show()
                } else {
                    row.duckAiModelTier.gone()
                }
                row.root.tag = model.id
                row.root.setOnClickListener { viewModel.onModelSelected(model.id) }
                container.addView(row.root)
            }
            renderedModelIds = ids
        }
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            child.findViewById<View>(R.id.duckAiModelSelectedIcon)?.isVisible = child.tag == selectedId
        }
    }

    private fun tierLabel(tier: UserTier): String? = when (tier) {
        UserTier.PLUS -> getString(R.string.subscriptionOnboardingDuckAiTierPlus).asTierSuffix()
        UserTier.PRO -> getString(R.string.subscriptionOnboardingDuckAiTierPro).asTierSuffix()
        UserTier.FREE -> null
    }

    private fun String.asTierSuffix(): String = "$TIER_SEPARATOR $this"

    companion object {
        private const val TIER_SEPARATOR = "·"
    }
}
