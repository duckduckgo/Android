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

package com.duckduckgo.subscriptions.impl.onboarding.completion

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.pir.api.dashboard.PirDashboardWebViewScreen
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.FragmentSubscriptionOnboardingCompletionBinding
import com.duckduckgo.subscriptions.impl.onboarding.completion.SubscriptionOnboardingCompletionViewModel.Command
import com.duckduckgo.subscriptions.impl.onboarding.completion.SubscriptionOnboardingCompletionViewModel.SummaryRow
import com.duckduckgo.subscriptions.impl.onboarding.completion.SubscriptionOnboardingCompletionViewModel.ViewState
import com.duckduckgo.subscriptions.impl.onboarding.welcome.launchOnboardingConfetti
import com.duckduckgo.subscriptions.impl.pir.PirActivity.Companion.PirScreenWithEmptyParams
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SubscriptionOnboardingCompletionFragment : DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_completion) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: FragmentSubscriptionOnboardingCompletionBinding by viewBinding()
    private val viewModel: SubscriptionOnboardingCompletionViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SubscriptionOnboardingCompletionViewModel::class.java]
    }

    // The bar fills once per appearance. Without this a later emission (or a rotation) would replay it.
    private var progressAnimated = false

    // The celebratory hero animation must start only once, not on every state emission.
    private var celebratoryConfigured = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.subscriptionOnboardingCompletionPrimaryButton.setOnClickListener {
            viewModel.onDoneClicked()
        }

        viewModel.viewState()
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun applyHandoffHeader() = with(binding) {
        subscriptionOnboardingCompletionIcon.setImageResource(R.drawable.duckai_ddg_feature_128)
        subscriptionOnboardingCompletionTitle.setText(R.string.subscriptionOnboardingCompletionDuckAiTitle)
        subscriptionOnboardingCompletionDescription.setText(R.string.subscriptionOnboardingCompletionDuckAiDescription)
        subscriptionOnboardingCompletionPrimaryButton.gone()
    }

    /** Everything is set up: play the animated hero, drop the "continue in settings" hint, and celebrate. */
    private fun applyCelebratoryHeader() {
        if (celebratoryConfigured) return
        celebratoryConfigured = true
        with(binding) {
            subscriptionOnboardingCompletionIcon.setAnimation(R.raw.subscription_hero_animation_badge)
            subscriptionOnboardingCompletionIcon.playAnimation()
            subscriptionOnboardingCompletionTitle.setText(R.string.subscriptionOnboardingCompletionCompleteTitle)
            subscriptionOnboardingCompletionDescription.gone()
            subscriptionOnboardingCompletionPrimaryButton.setText(R.string.subscriptionOnboardingCompletionCompletePrimaryButton)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            Command.OpenPirDashboard -> globalActivityStarter.start(requireContext(), PirDashboardWebViewScreen)
            Command.OpenPirDesktop -> globalActivityStarter.start(requireContext(), PirScreenWithEmptyParams)
            Command.ShowPirUnavailableDialog -> TextAlertDialogBuilder(requireContext())
                .setTitle(R.string.pirStorageUnavailableDialogTitle)
                .setMessage(R.string.pirStorageUnavailableDialogMessage)
                .setPositiveButton(R.string.pirStorageUnavailableDialogButton, GHOST)
                .show()
        }
    }

    private fun render(viewState: ViewState) {
        when {
            viewState.handoff -> applyHandoffHeader()
            viewState.celebratory -> applyCelebratoryHeader()
        }

        if (viewState.rows.isEmpty()) return

        binding.subscriptionOnboardingCompletionPercentage.text =
            getString(R.string.subscriptionOnboardingCompletionPercentage, viewState.completionPercentage)

        renderRows(viewState.rows)
        animateProgress(viewState.completionPercentage, celebrate = viewState.celebratory)
    }

    private fun renderRows(rows: List<SummaryRow>) {
        val container = binding.subscriptionOnboardingCompletionRows
        container.removeAllViews()
        rows.forEach { row ->
            val rowView = LayoutInflater.from(container.context)
                .inflate(R.layout.view_subscription_onboarding_completion_row, container, false) as OneLineListItem
            rowView.setPrimaryTextResource(row.labelResId)
            if (row.completed) {
                rowView.setAnimatedCheck()
            } else {
                rowView.setLeadingIconResource(row.pendingIconResId)
            }
            if (row.clickable) {
                rowView.setClickListener { viewModel.onPirRowClicked() }
            }
            container.addView(rowView)
        }
    }

    private fun OneLineListItem.setAnimatedCheck() {
        val lottieDrawable = LottieDrawable()
        setLeadingIconDrawable(lottieDrawable)
        LottieCompositionFactory.fromRawRes(context, R.raw.check_color)
            .addListener { composition ->
                lottieDrawable.composition = composition
                lottieDrawable.playAnimation()
            }
    }

    private fun animateProgress(percentage: Int, celebrate: Boolean) {
        if (progressAnimated) return
        progressAnimated = true

        val track = binding.subscriptionOnboardingCompletionProgressTrack
        val fill = binding.subscriptionOnboardingCompletionProgressFill
        track.doOnLayout {
            val targetWidth = (it.width * percentage / 100f).toInt()
            ValueAnimator.ofInt(0, targetWidth).apply {
                duration = PROGRESS_ANIMATION_DURATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    fill.updateLayoutParams { width = animator.animatedValue as Int }
                }
                if (celebrate) doOnEnd { binding.subscriptionOnboardingCompletionKonfetti.launchOnboardingConfetti() }
                start()
            }
        }
    }

    companion object {
        private const val PROGRESS_ANIMATION_DURATION_MS = 1000L
    }
}
