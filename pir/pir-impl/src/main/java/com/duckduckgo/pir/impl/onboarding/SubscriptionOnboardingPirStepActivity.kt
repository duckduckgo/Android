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

package com.duckduckgo.pir.impl.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.pir.api.PirScreens.PirOnboardingStepScreen
import com.duckduckgo.pir.impl.R
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewActivity
import com.duckduckgo.pir.impl.databinding.ActivitySubscriptionOnboardingPirStepBinding
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirOnboardingStepScreen::class)
class SubscriptionOnboardingPirStepActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivitySubscriptionOnboardingPirStepBinding by viewBinding()

    private val viewModel: SubscriptionOnboardingPirStepViewModel by bindViewModel()

    private var activateLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.MISC)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        binding.includeToolbar.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
        supportActionBar?.title = ""

        val surfaceColor = getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface)
        binding.includeToolbar.appBarLayout.setBackgroundColor(surfaceColor)
        binding.includeToolbar.toolbar.setBackgroundColor(surfaceColor)

        binding.pirOnboardingStepIcon.setImageResource(R.drawable.personal_information_remover_feature_128)
        binding.pirOnboardingStepTitle.setText(R.string.pirOnboardingStepTitle)
        binding.pirOnboardingStepDescription.setText(R.string.pirOnboardingStepDescription)
        layoutInflater.inflate(R.layout.content_pir_onboarding_step, binding.pirOnboardingStepContent, true)

        binding.pirOnboardingStepActivateButton.setOnClickListener {
            activateLaunched = true
            startActivity(
                Intent(this, PirDashboardWebViewActivity::class.java)
                    .putExtra(PirDashboardWebViewActivity.EXTRA_SHOW_CLOSE_BUTTON, true),
            )
        }

        if (edgeToEdgeEnabled) {
            edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
            edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout, installScrim = false)
            edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.pirOnboardingStepScrollView)
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning from the PIR flow: close straight back to the completion screen, marking the step done
        // only if the user actually started a scan.
        if (!activateLaunched || isFinishing) return
        lifecycleScope.launch {
            viewModel.completeIfScanStarted()
            finish()
        }
    }
}
