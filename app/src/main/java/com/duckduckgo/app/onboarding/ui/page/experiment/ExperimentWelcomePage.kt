/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.experiment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomeExperimentBinding
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePage.Companion.PreOnboardingDialogType.CELEBRATION
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePage.Companion.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePage.Companion.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePageViewModel.Command.ShowDefaultBrowserDialog
import com.duckduckgo.app.onboarding.ui.page.experiment.ExperimentWelcomePageViewModel.Command.ShowSuccessDialog
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class ExperimentWelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_experiment) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private val binding: ContentOnboardingWelcomeExperimentBinding by viewBinding()
    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[ExperimentWelcomePageViewModel::class.java]
    }

    private var ctaText: String = ""
    private var hikerAnimation: ViewPropertyAnimatorCompat? = null
    private var welcomeAnimation: ViewPropertyAnimatorCompat? = null
    private var typingAnimation: ViewPropertyAnimatorCompat? = null
    private var welcomeAnimationFinished = false

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (view?.windowVisibility == View.VISIBLE) {
            scheduleWelcomeAnimation(ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = ContentOnboardingWelcomeExperimentBinding.inflate(inflater, container, false)
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            when (it) {
                is ShowComparisonChart -> configureDaxCta(COMPARISON_CHART)
                is ShowDefaultBrowserDialog -> showDefaultBrowserDialog(it.intent)
                is ShowSuccessDialog -> configureDaxCta(CELEBRATION)
                is Finish -> onContinuePressed()
            }
        }.launchIn(lifecycleScope)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requestNotificationsPermissions()
        setSkipAnimationListener()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (requestCode == DEFAULT_BROWSER_ROLE_MANAGER_DIALOG) {
            if (resultCode == Activity.RESULT_OK) {
                viewModel.onDefaultBrowserSet()
            } else {
                viewModel.onDefaultBrowserNotSet()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @SuppressLint("InlinedApi")
    private fun requestNotificationsPermissions() {
        if (appBuildConfig.sdkInt >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleWelcomeAnimation()
        }
    }

    private fun configureDaxCta(onboardingDialogType: PreOnboardingDialogType) {
        context?.let {
            when (onboardingDialogType) {
                INITIAL -> {
                    ctaText = it.getString(R.string.preOnboardingDaxDialog1Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.dialogTextCta.textInDialog = ctaText.html(it)
                    binding.daxDialogCta.experimentDialogContentImage.alpha = MIN_ALPHA
                    binding.daxDialogCta.experimentDialogContentText.gone()
                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                    binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INITIAL) }
                    ViewCompat.animate(binding.daxDialogCta.primaryCta).alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION).startDelay = ANIMATION_DELAY
                }

                COMPARISON_CHART -> {
                    binding.daxDialogCta.dialogTextCta.text = ""
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    ctaText = it.getString(R.string.preOnboardingDaxDialog2Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.dialogTextCta.textInDialog = ctaText.html(it)
                    binding.daxDialogCta.experimentDialogContentImage.alpha = MIN_ALPHA
                    binding.daxDialogCta.experimentDialogContentImage.setImageResource(R.drawable.comparison_chart)
                    ViewCompat.animate(binding.daxDialogCta.experimentDialogContentImage)
                        .alpha(MAX_ALPHA)
                        .setDuration(ANIMATION_DURATION).startDelay = ANIMATION_DELAY
                    binding.daxDialogCta.experimentDialogContentText.gone()
                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog2Button)
                    binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(COMPARISON_CHART) }
                    ViewCompat.animate(binding.daxDialogCta.primaryCta).alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION).startDelay = ANIMATION_DELAY
                    scheduleTypingAnimation()
                }

                CELEBRATION -> {
                    binding.daxDialogCta.dialogTextCta.text = ""
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    ctaText = it.getString(R.string.preOnboardingDaxDialog3Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.dialogTextCta.textInDialog = ctaText.html(it)
                    binding.daxDialogCta.experimentDialogContentImage.alpha = MIN_ALPHA
                    binding.daxDialogCta.experimentDialogContentImage.setImageResource(R.drawable.ic_success_128)
                    ViewCompat.animate(binding.daxDialogCta.experimentDialogContentImage)
                        .alpha(MAX_ALPHA)
                        .setDuration(ANIMATION_DURATION).startDelay = ANIMATION_DELAY
                    binding.daxDialogCta.experimentDialogContentText.text = it.getString(R.string.preOnboardingDaxDialog3Content)
                    binding.daxDialogCta.experimentDialogContentText.show()
                    ViewCompat.animate(binding.daxDialogCta.experimentDialogContentText)
                        .alpha(MAX_ALPHA)
                        .setDuration(ANIMATION_DURATION).startDelay = ANIMATION_DELAY
                    binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog3Button)
                    binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(CELEBRATION) }
                    ViewCompat.animate(binding.daxDialogCta.primaryCta).alpha(MAX_ALPHA).setDuration(ANIMATION_DURATION).startDelay = ANIMATION_DELAY
                    scheduleTypingAnimation()
                    // TODO experiment setConfetti
                }
            }
        }
    }

    private fun setSkipAnimationListener() {
        binding.longDescriptionContainer.setOnClickListener {
            if (binding.daxDialogCta.dialogTextCta.hasAnimationStarted()) {
                finishTypingAnimation()
            } else if (!welcomeAnimationFinished) {
                welcomeAnimation?.cancel()
                hikerAnimation?.cancel()
                scheduleWelcomeAnimation(0L)
            }
            welcomeAnimationFinished = true
        }
    }

    private fun scheduleWelcomeAnimation(startDelay: Long = ANIMATION_DELAY) {
        ViewCompat.animate(binding.foregroundImageView)
            .alpha(MIN_ALPHA)
            .setDuration(ANIMATION_DURATION).startDelay = startDelay
        welcomeAnimation = ViewCompat.animate(binding.welcomeContent as View)
            .alpha(MIN_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .setStartDelay(startDelay)
            .withEndAction {
                configureDaxCta(INITIAL)
                scheduleTypingAnimation()
            }
    }

    private fun scheduleTypingAnimation() {
        typingAnimation = ViewCompat.animate(binding.daxDialogCta.daxCtaContainer)
            .alpha(MAX_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                welcomeAnimationFinished = true
                binding.daxDialogCta.dialogTextCta.startTypingAnimation(ctaText)
            }
    }

    private fun finishTypingAnimation() {
        welcomeAnimation?.cancel()
        hikerAnimation?.cancel()
        binding.daxDialogCta.dialogTextCta.finishAnimation()
    }

    private fun showDefaultBrowserDialog(intent: Intent) {
        startActivityForResult(intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
    }

    companion object {

        enum class PreOnboardingDialogType {
            INITIAL,
            COMPARISON_CHART,
            CELEBRATION,
        }

        private const val MIN_ALPHA = 0f
        private const val MAX_ALPHA = 1f
        private const val ANIMATION_DURATION = 400L
        private const val ANIMATION_DELAY = 1400L
        private const val ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED = 800L

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}
