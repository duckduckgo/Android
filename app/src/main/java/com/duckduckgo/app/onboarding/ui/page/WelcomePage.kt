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

package com.duckduckgo.app.onboarding.ui.page

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageBinding
import com.duckduckgo.app.notificationpromptexperiment.NotificationPromptExperimentManager
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.OnboardingSkipped
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.SetAddressBarPositionOptions
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowAddressBarPositionDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowDefaultBrowserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialReinstallUserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowSkipOnboardingOption
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentManager
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class WelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var onboardingDesignExperimentManager: OnboardingDesignExperimentManager

    @Inject
    lateinit var notificationPromptExperimentManager: NotificationPromptExperimentManager

    private val binding: ContentOnboardingWelcomePageBinding by viewBinding()
    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[WelcomePageViewModel::class.java]
    }

    private var hikerAnimation: ViewPropertyAnimatorCompat? = null
    private var welcomeAnimation: ViewPropertyAnimatorCompat? = null
    private var typingAnimation: ViewPropertyAnimatorCompat? = null
    private var welcomeAnimationFinished = false

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted ->
        if (permissionGranted) {
            viewModel.notificationRuntimePermissionGranted()
        }
        if (view?.windowVisibility == View.VISIBLE) {
            scheduleWelcomeAnimation(ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            when (it) {
                is ShowInitialReinstallUserDialog -> configureDaxCta(INITIAL_REINSTALL_USER)
                is ShowInitialDialog -> configureDaxCta(INITIAL)
                is ShowComparisonChart -> configureDaxCta(COMPARISON_CHART)
                is ShowSkipOnboardingOption -> configureDaxCta(SKIP_ONBOARDING_OPTION)
                is ShowDefaultBrowserDialog -> showDefaultBrowserDialog(it.intent)
                is ShowAddressBarPositionDialog -> configureDaxCta(ADDRESS_BAR_POSITION)
                is Finish -> onContinuePressed()
                is OnboardingSkipped -> onSkipPressed()
                is SetAddressBarPositionOptions -> setAddressBarPositionOptions(it.defaultOption)
            }
        }.launchIn(lifecycleScope)
    }

    private fun setAddressBarPositionOptions(defaultOption: Boolean) {
        if (defaultOption) {
            binding.daxDialogCta.addressBarPosition.option1.setBackgroundResource(R.drawable.background_preonboarding_option_selected)
            binding.daxDialogCta.addressBarPosition.option1Switch.isChecked = true
            binding.daxDialogCta.addressBarPosition.option2.setBackgroundResource(R.drawable.background_preonboarding_option)
            binding.daxDialogCta.addressBarPosition.option2Switch.isChecked = false
        } else {
            binding.daxDialogCta.addressBarPosition.option1.setBackgroundResource(R.drawable.background_preonboarding_option)
            binding.daxDialogCta.addressBarPosition.option1Switch.isChecked = false
            binding.daxDialogCta.addressBarPosition.option2.setBackgroundResource(R.drawable.background_preonboarding_option_selected)
            binding.daxDialogCta.addressBarPosition.option2Switch.isChecked = true
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setBackgroundRes(
            if (appTheme.isLightModeEnabled()) {
                R.drawable.onboarding_background_bitmap_light
            } else {
                R.drawable.onboarding_background_bitmap_dark
            },
        )

        if (onboardingDesignExperimentManager.isModifiedControlEnrolledAndEnabled()) {
            scheduleWelcomeAnimation()
        } else {
            requestNotificationsPermissions()
        }

        setSkipAnimationListener()
    }

    override fun onResume() {
        super.onResume()
        applyFullScreenFlags()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        welcomeAnimation?.cancel()
        typingAnimation?.cancel()
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
            if (notificationPromptExperimentManager.isControl()) {
                viewModel.notificationRuntimePermissionRequested()
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scheduleWelcomeAnimation()
            }
        } else {
            scheduleWelcomeAnimation()
        }
    }

    private fun configureDaxCta(onboardingDialogType: PreOnboardingDialogType) {
        context?.let {
            var afterAnimation: () -> Unit = {}
            viewModel.onDialogShown(onboardingDialogType)
            when (onboardingDialogType) {
                INITIAL_REINSTALL_USER -> {
                    binding.daxDialogCta.root.show()
                    binding.daxDialogCta.progressBarText.gone()
                    binding.daxDialogCta.progressBar.gone()
                    binding.daxDialogCta.descriptionCta.gone()
                    binding.daxDialogCta.secondaryCta.show()

                    val ctaText = it.getString(R.string.preOnboardingDaxDialog1Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.daxDialogContentImage.gone()
                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INITIAL_REINSTALL_USER) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.secondaryCta.text = it.getString(R.string.preOnboardingDaxDialog1SecondaryButton)
                        binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked(INITIAL_REINSTALL_USER) }
                        binding.daxDialogCta.secondaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                    scheduleTypingAnimation(ctaText) { afterAnimation() }
                }

                INITIAL -> {
                    binding.daxDialogCta.root.show()
                    binding.daxDialogCta.progressBarText.gone()
                    binding.daxDialogCta.progressBar.gone()
                    binding.daxDialogCta.descriptionCta.gone()
                    binding.daxDialogCta.secondaryCta.gone()

                    val ctaText = it.getString(R.string.preOnboardingDaxDialog1Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.daxDialogContentImage.gone()
                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INITIAL) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                    scheduleTypingAnimation(ctaText) { afterAnimation() }
                }

                COMPARISON_CHART -> {
                    binding.daxDialogCta.descriptionCta.gone()
                    binding.daxDialogCta.secondaryCta.gone()
                    binding.daxDialogCta.dialogTextCta.text = ""
                    TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
                    binding.daxDialogCta.progressBarText.show()
                    binding.daxDialogCta.progressBarText.text = "1 / 2"
                    binding.daxDialogCta.progressBar.show()
                    binding.daxDialogCta.progressBar.progress = 1
                    val ctaText = it.getString(R.string.preOnboardingDaxDialog2Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.comparisonChart.root.show()
                    binding.daxDialogCta.comparisonChart.root.alpha = MIN_ALPHA

                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog2Button)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(COMPARISON_CHART) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.comparisonChart.root.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                    scheduleTypingAnimation(ctaText) { afterAnimation() }
                }

                SKIP_ONBOARDING_OPTION -> {
                    binding.daxDialogCta.descriptionCta.show()
                    binding.daxDialogCta.descriptionCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.secondaryCta.show()
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.secondaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.dialogTextCta.text = ""

                    TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())

                    val ctaDialog3Text = it.getString(R.string.preOnboardingDaxDialog3Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaDialog3Text.html(it)
                    val ctaDialog3Description = it.getString(R.string.preOnboardingDaxDialog3Text)
                    binding.daxDialogCta.descriptionCta.text = ctaDialog3Description.html(it)
                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        binding.daxDialogCta.descriptionCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog3Button)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(SKIP_ONBOARDING_OPTION) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.secondaryCta.text = it.getString(R.string.preOnboardingDaxDialog3SecondaryButton)
                        binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked(SKIP_ONBOARDING_OPTION) }
                        binding.daxDialogCta.secondaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                    scheduleTypingAnimation(ctaDialog3Text) { afterAnimation() }
                }

                ADDRESS_BAR_POSITION -> {
                    binding.daxDialogCta.descriptionCta.gone()
                    binding.daxDialogCta.secondaryCta.gone()
                    binding.daxDialogCta.dialogTextCta.text = ""
                    binding.daxDialogCta.comparisonChart.root.gone()
                    TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
                    binding.daxDialogCta.progressBarText.show()
                    binding.daxDialogCta.progressBarText.text = "2 / 2"
                    binding.daxDialogCta.progressBar.show()
                    binding.daxDialogCta.progressBar.progress = 2
                    val ctaText = it.getString(R.string.preOnboardingAddressBarTitle).run {
                        if (onboardingDesignExperimentManager.isModifiedControlEnrolledAndEnabled()) {
                            preventWidows()
                        } else {
                            this
                        }
                    }
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.addressBarPosition.root.show()
                    binding.daxDialogCta.addressBarPosition.root.alpha = MIN_ALPHA

                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        setAddressBarPositionOptions(true)
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingAddressBarOkButton)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(ADDRESS_BAR_POSITION) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.addressBarPosition.option1.setOnClickListener {
                            viewModel.onAddressBarPositionOptionSelected(true)
                        }
                        binding.daxDialogCta.addressBarPosition.option2.setOnClickListener {
                            viewModel.onAddressBarPositionOptionSelected(false)
                        }
                        binding.daxDialogCta.addressBarPosition.root.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }

                    scheduleTypingAnimation(ctaText) { afterAnimation() }
                }
            }
            binding.sceneBg.setOnClickListener { afterAnimation() }
            binding.daxDialogCta.cardContainer.setOnClickListener { afterAnimation() }
        }
    }

    private fun setSkipAnimationListener() {
        val dialogAnimationStarted = binding.daxDialogCta.dialogTextCta.hasAnimationStarted()
        binding.longDescriptionContainer.setOnClickListener {
            if (dialogAnimationStarted) {
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
                viewModel.loadDaxDialog()
            }
    }

    private fun scheduleTypingAnimation(
        ctaText: String,
        afterAnimation: () -> Unit = {},
    ) {
        typingAnimation = ViewCompat.animate(binding.daxDialogCta.daxCtaContainer)
            .alpha(MAX_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                welcomeAnimationFinished = true
                binding.daxDialogCta.dialogTextCta.startTypingAnimation(ctaText, afterAnimation = afterAnimation)
            }
    }

    private fun finishTypingAnimation() {
        welcomeAnimation?.cancel()
        hikerAnimation?.cancel()
    }

    private fun showDefaultBrowserDialog(intent: Intent) {
        startActivityForResult(intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
    }

    private fun applyFullScreenFlags() {
        activity?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.BLACK
        }
        ViewCompat.requestApplyInsets(binding.longDescriptionContainer)
    }

    private fun setBackgroundRes(backgroundRes: Int) {
        binding.sceneBg.setImageResource(backgroundRes)
    }

    companion object {
        private const val MIN_ALPHA = 0f
        private const val MAX_ALPHA = 1f
        private const val ANIMATION_DURATION = 400L
        private const val ANIMATION_DELAY = 1400L
        private const val ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED = 800L

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}
