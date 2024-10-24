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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
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
import com.duckduckgo.app.onboarding.ui.page.WelcomePage.Companion.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.WelcomePage.Companion.PreOnboardingDialogType.CELEBRATION
import com.duckduckgo.app.onboarding.ui.page.WelcomePage.Companion.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.WelcomePage.Companion.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.SetAddressBarPositionOptions
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.SetBackgroundResource
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowAddressBarPositionDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowDefaultBrowserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowExperimentComparisonChart
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowExperimentInitialDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowSuccessDialog
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size

@InjectWith(FragmentScope::class)
class WelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var appTheme: AppTheme

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = ContentOnboardingWelcomePageBinding.inflate(inflater, container, false)
        viewModel.setBackgroundResource(appTheme.isLightModeEnabled())
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            when (it) {
                is ShowInitialDialog -> configureDaxCta(INITIAL)
                is ShowExperimentInitialDialog -> configureExperimentDaxDialog(INITIAL)
                is ShowComparisonChart -> configureDaxCta(COMPARISON_CHART)
                is ShowExperimentComparisonChart -> configureExperimentDaxDialog(COMPARISON_CHART)
                is ShowDefaultBrowserDialog -> showDefaultBrowserDialog(it.intent)
                is ShowSuccessDialog -> configureDaxCta(CELEBRATION)
                is ShowAddressBarPositionDialog -> configureExperimentDaxDialog(ADDRESS_BAR_POSITION)
                is Finish -> onContinuePressed()
                is SetBackgroundResource -> setBackgroundRes(it.backgroundRes)
                is SetAddressBarPositionOptions -> setAddressBarPositionOptions(it.defaultOption)
            }
        }.launchIn(lifecycleScope)
        return binding.root
    }

    private fun setAddressBarPositionOptions(defaultOption: Boolean) {
        if (defaultOption) {
            binding.daxDialogCtaExperiment.addressBarPosition.option1.setBackgroundResource(R.drawable.background_preonboarding_option_selected)
            binding.daxDialogCtaExperiment.addressBarPosition.option1Switch.isChecked = true
            binding.daxDialogCtaExperiment.addressBarPosition.option2.setBackgroundResource(R.drawable.background_preonboarding_option)
            binding.daxDialogCtaExperiment.addressBarPosition.option2Switch.isChecked = false
        } else {
            binding.daxDialogCtaExperiment.addressBarPosition.option1.setBackgroundResource(R.drawable.background_preonboarding_option)
            binding.daxDialogCtaExperiment.addressBarPosition.option1Switch.isChecked = false
            binding.daxDialogCtaExperiment.addressBarPosition.option2.setBackgroundResource(R.drawable.background_preonboarding_option_selected)
            binding.daxDialogCtaExperiment.addressBarPosition.option2Switch.isChecked = true
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requestNotificationsPermissions()
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
            viewModel.notificationRuntimePermissionRequested()
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleWelcomeAnimation()
        }
    }

    private fun configureExperimentDaxDialog(onboardingDialogType: PreOnboardingDialogType) {
        context?.let {
            viewModel.onDialogShown(onboardingDialogType)
            when (onboardingDialogType) {
                INITIAL -> {
                    binding.daxDialogCta.root.gone()
                    binding.daxDialogCtaExperiment.root.show()
                    binding.daxDialogCtaExperiment.progressBarText.gone()
                    binding.daxDialogCtaExperiment.progressBar.gone()

                    val ctaText = it.getString(R.string.highlightsPreOnboardingDaxDialog1Title)
                    binding.daxDialogCtaExperiment.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCtaExperiment.daxDialogContentImage.gone()

                    scheduleExperimentTypingAnimation(ctaText) {
                        binding.daxDialogCtaExperiment.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                        binding.daxDialogCtaExperiment.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INITIAL) }
                        binding.daxDialogCtaExperiment.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                }

                COMPARISON_CHART -> {
                    binding.daxDialogCtaExperiment.dialogTextCta.text = ""
                    TransitionManager.beginDelayedTransition(binding.daxDialogCtaExperiment.cardView, AutoTransition())
                    binding.daxDialogCtaExperiment.progressBarText.show()
                    binding.daxDialogCtaExperiment.progressBarText.text = "1 / 2"
                    binding.daxDialogCtaExperiment.progressBar.show()
                    binding.daxDialogCtaExperiment.progressBar.progress = 1
                    val ctaText = it.getString(R.string.highlightsPreOnboardingDaxDialog2Title)
                    binding.daxDialogCtaExperiment.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCtaExperiment.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCtaExperiment.comparisonChart.root.show()
                    binding.daxDialogCtaExperiment.comparisonChart.root.alpha = MIN_ALPHA

                    binding.daxDialogCtaExperiment.comparisonChart.featureIcon1.show()
                    binding.daxDialogCtaExperiment.comparisonChart.featureIcon2.show()
                    binding.daxDialogCtaExperiment.comparisonChart.featureIcon3.show()
                    binding.daxDialogCtaExperiment.comparisonChart.featureIcon4.show()
                    binding.daxDialogCtaExperiment.comparisonChart.featureIcon5.show()
                    binding.daxDialogCtaExperiment.comparisonChart.feature3.text = it.getString(R.string.highlightsPreOnboardingComparisonChartItem3)
                    binding.daxDialogCtaExperiment.comparisonChart.feature4.text = it.getString(R.string.highlightsPreOnboardingComparisonChartItem4)
                    binding.daxDialogCtaExperiment.comparisonChart.feature5.text = it.getString(R.string.highlightsPreOnboardingComparisonChartItem5)

                    scheduleExperimentTypingAnimation(ctaText) {
                        binding.daxDialogCtaExperiment.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog2Button)
                        binding.daxDialogCtaExperiment.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(COMPARISON_CHART) }
                        binding.daxDialogCtaExperiment.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCtaExperiment.comparisonChart.root.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                }

                ADDRESS_BAR_POSITION -> {
                    binding.daxDialogCtaExperiment.dialogTextCta.text = ""
                    binding.daxDialogCtaExperiment.comparisonChart.root.gone()
                    TransitionManager.beginDelayedTransition(binding.daxDialogCtaExperiment.cardView, AutoTransition())
                    binding.daxDialogCtaExperiment.progressBarText.show()
                    binding.daxDialogCtaExperiment.progressBarText.text = "2 / 2"
                    binding.daxDialogCtaExperiment.progressBar.show()
                    binding.daxDialogCtaExperiment.progressBar.progress = 2
                    val ctaText = it.getString(R.string.highlightsPreOnboardingAddressBarTitle)
                    binding.daxDialogCtaExperiment.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCtaExperiment.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCtaExperiment.addressBarPosition.root.show()
                    binding.daxDialogCtaExperiment.addressBarPosition.root.alpha = MIN_ALPHA

                    scheduleExperimentTypingAnimation(ctaText) {
                        setAddressBarPositionOptions(true)
                        binding.daxDialogCtaExperiment.primaryCta.text = it.getString(R.string.highlightsPreOnboardingAddressBarOkButton)
                        binding.daxDialogCtaExperiment.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(ADDRESS_BAR_POSITION) }
                        binding.daxDialogCtaExperiment.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCtaExperiment.addressBarPosition.option1.setOnClickListener {
                            viewModel.onAddressBarPositionOptionSelected(true)
                        }
                        binding.daxDialogCtaExperiment.addressBarPosition.option2.setOnClickListener {
                            viewModel.onAddressBarPositionOptionSelected(false)
                        }
                        binding.daxDialogCtaExperiment.addressBarPosition.root.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                }

                CELEBRATION -> {} // No available for experiment
            }
        }
    }

    private fun configureDaxCta(onboardingDialogType: PreOnboardingDialogType) {
        context?.let {
            viewModel.onDialogShown(onboardingDialogType)
            when (onboardingDialogType) {
                INITIAL -> {
                    binding.daxDialogCtaExperiment.root.gone()
                    binding.daxDialogCta.root.show()
                    val ctaText = it.getString(R.string.preOnboardingDaxDialog1Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.daxDialogContentImage.gone()

                    scheduleTypingAnimation(ctaText) {
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog1Button)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INITIAL) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                }

                COMPARISON_CHART -> {
                    binding.daxDialogCta.dialogTextCta.text = ""
                    TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
                    val ctaText = it.getString(R.string.preOnboardingDaxDialog2Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.comparisonChart.root.show()
                    binding.daxDialogCta.comparisonChart.root.alpha = MIN_ALPHA

                    scheduleTypingAnimation(ctaText) {
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog2Button)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(COMPARISON_CHART) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.comparisonChart.root.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                }

                CELEBRATION -> {
                    binding.daxDialogCta.dialogTextCta.text = ""
                    binding.daxDialogCta.comparisonChart.root.gone()
                    binding.daxDialogCta.addressBarPosition.root.gone()
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    val ctaText = it.getString(R.string.preOnboardingDaxDialog3Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.daxDialogContentImage.alpha = MIN_ALPHA
                    binding.daxDialogCta.daxDialogContentImage.show()
                    binding.daxDialogCta.daxDialogContentImage.setImageResource(R.drawable.ic_success_128)
                    launchKonfetti()

                    scheduleTypingAnimation(ctaText) {
                        ViewCompat.animate(binding.daxDialogCta.daxDialogContentImage).alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog3Button)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(CELEBRATION) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                }

                ADDRESS_BAR_POSITION -> {} // No available for control group
            }
        }
    }

    private fun setSkipAnimationListener() {
        val dialogAnimationStarted = binding.daxDialogCta.dialogTextCta.hasAnimationStarted() ||
            binding.daxDialogCtaExperiment.dialogTextCta.hasAnimationStarted()
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

    private fun scheduleTypingAnimation(ctaText: String, afterAnimation: () -> Unit = {}) {
        typingAnimation = ViewCompat.animate(binding.daxDialogCta.daxCtaContainer)
            .alpha(MAX_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                welcomeAnimationFinished = true
                binding.daxDialogCta.dialogTextCta.startTypingAnimation(ctaText, afterAnimation = afterAnimation)
            }
    }

    private fun scheduleExperimentTypingAnimation(ctaText: String, afterAnimation: () -> Unit = {}) {
        typingAnimation = ViewCompat.animate(binding.daxDialogCtaExperiment.daxCtaContainer)
            .alpha(MAX_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                welcomeAnimationFinished = true
                binding.daxDialogCtaExperiment.dialogTextCta.startTypingAnimation(ctaText, afterAnimation = afterAnimation)
            }
    }

    private fun finishTypingAnimation() {
        welcomeAnimation?.cancel()
        hikerAnimation?.cancel()
    }

    private fun showDefaultBrowserDialog(intent: Intent) {
        startActivityForResult(intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
    }

    private fun launchKonfetti() {
        val magenta = ResourcesCompat.getColor(resources, com.duckduckgo.mobile.android.R.color.magenta, null)
        val blue = ResourcesCompat.getColor(resources, com.duckduckgo.mobile.android.R.color.blue30, null)
        val purple = ResourcesCompat.getColor(resources, com.duckduckgo.mobile.android.R.color.purple, null)
        val green = ResourcesCompat.getColor(resources, com.duckduckgo.mobile.android.R.color.green, null)
        val yellow = ResourcesCompat.getColor(resources, com.duckduckgo.mobile.android.R.color.yellow, null)

        val displayWidth = resources.displayMetrics.widthPixels

        binding.setAsDefaultKonfetti.build()
            .addColors(magenta, blue, purple, green, yellow)
            .setDirection(0.0, 359.0)
            .setSpeed(4f, 9f)
            .setFadeOutEnabled(true)
            .setTimeToLive(1500L)
            .addShapes(Shape.Rectangle(1f))
            .addSizes(Size(8))
            .setPosition(displayWidth / 2f, displayWidth / 2f, -50f, -50f)
            .streamFor(60, 2000L)
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

        enum class PreOnboardingDialogType {
            INITIAL,
            COMPARISON_CHART,
            ADDRESS_BAR_POSITION,
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
