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

package com.duckduckgo.subscriptions.impl.ui.onboarding

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepNavigator
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.FragmentSubscriptionOnboardingStepBinding
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin host for a single onboarding step. It resolves the step plugin by name, sets the host
 * toolbar title and inserts the plugin's own view (which owns its header, content and buttons).
 */
@InjectWith(FragmentScope::class)
class SubscriptionOnboardingStepFragment : DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_step) {

    @Inject
    lateinit var stepPlugins: ActivePluginPoint<SubscriptionOnboardingStepPlugin>

    private val binding: FragmentSubscriptionOnboardingStepBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pluginName = arguments?.getString(KEY_PLUGIN_NAME) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val plugin = stepPlugins.getPlugins().firstOrNull { it.name == pluginName } ?: return@launch
            bindStep(plugin)
        }
    }

    private fun bindStep(plugin: SubscriptionOnboardingStepPlugin) {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.setTitle(plugin.toolbarTitle)

        val navigator = SubscriptionOnboardingStepNavigator {
            (requireActivity() as SubscriptionOnboardingStepHost).onStepCompleted()
        }
        binding.stepContainer.removeAllViews()
        binding.stepContainer.addView(
            plugin.getOnboardingStepView(requireContext(), navigator),
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
        )
    }

    companion object {
        private const val KEY_PLUGIN_NAME = "pluginName"

        fun instance(pluginName: String): SubscriptionOnboardingStepFragment =
            SubscriptionOnboardingStepFragment().apply {
                arguments = bundleOf(KEY_PLUGIN_NAME to pluginName)
            }
    }
}
