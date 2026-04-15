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

@file:SuppressLint("NoImplImportsInAppModule")

package com.duckduckgo.app.onboarding.ui.page

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INPUT_SCREEN
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INPUT_SCREEN_PREVIEW
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SYNC_RESTORE
import com.duckduckgo.app.onboarding.ui.page.WelcomePage.InputMode.*
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.FinishAndSubmitChatPrompt
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.FinishAndSubmitSearchQuery
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.OnboardingSkipped
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.SetAddressBarPositionOptions
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowAddressBarPositionDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowDefaultBrowserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialReinstallUserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInputScreenDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInputScreenPreviewDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowSkipOnboardingOption
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowSyncRestoreDialog
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.duckduckgo.duckchat.impl.R as DuckChatR
import com.duckduckgo.mobile.android.R as CommonR

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
    private var currentInputMode: InputMode = SEARCH

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
                is ShowSyncRestoreDialog -> configureDaxCta(SYNC_RESTORE)
                is ShowInitialReinstallUserDialog -> configureDaxCta(INITIAL_REINSTALL_USER, showDuckAiCopy = it.showDuckAiCopy)
                is ShowInitialDialog -> configureDaxCta(INITIAL, showDuckAiCopy = it.showDuckAiCopy)
                is ShowComparisonChart -> configureDaxCta(COMPARISON_CHART, showDuckAiCopy = it.showDuckAiCopy)
                is ShowSkipOnboardingOption -> configureDaxCta(SKIP_ONBOARDING_OPTION)
                is ShowDefaultBrowserDialog -> showDefaultBrowserDialog(it.intent)
                is ShowAddressBarPositionDialog -> configureDaxCta(ADDRESS_BAR_POSITION, it.showSplitOption)
                is ShowInputScreenDialog -> configureDaxCta(INPUT_SCREEN, showDuckAiCopy = it.showDuckAiCopy)
                is ShowInputScreenPreviewDialog -> configureInputScreenPreviewDialog(
                    searchSuggestions = it.searchSuggestions,
                    chatSuggestions = it.chatSuggestions,
                    defaultInputMode = if (it.duckAiDefault) CHAT else SEARCH,
                )
                is Finish -> onContinuePressed()
                is FinishAndSubmitSearchQuery -> {
                    (activity as? OnboardingActivity)?.finishAndSubmitSearchQuery(it.query)
                }
                is FinishAndSubmitChatPrompt -> {
                    (activity as? OnboardingActivity)?.finishAndSubmitChatPrompt(it.prompt)
                }
                is OnboardingSkipped -> onSkipPressed()
                is SetAddressBarPositionOptions -> setAddressBarPositionOptions(it.selectedOption)
            }
        }.launchIn(lifecycleScope)
    }

    private fun setAddressBarPositionOptions(selectedOption: OmnibarType) {
        context?.let { ctx ->
            val isLightMode = appTheme.isLightModeEnabled()

            // Configure top option
            val topButton = OmnibarTypeToggleButton.Top(
                isActive = selectedOption == OmnibarType.SINGLE_TOP,
                isLightMode = isLightMode,
            )
            binding.daxDialogCta.addressBarPosition.topOmnibarToggleImage.setImageResource(topButton.imageRes)
            binding.daxDialogCta.addressBarPosition.topOmnibarToggleCheck.setImageResource(topButton.checkRes)

            // Configure bottom option
            val bottomButton = OmnibarTypeToggleButton.Bottom(
                isActive = selectedOption == OmnibarType.SINGLE_BOTTOM,
                isLightMode = isLightMode,
            )
            binding.daxDialogCta.addressBarPosition.bottomOmnibarToggleImage.setImageResource(bottomButton.imageRes)
            binding.daxDialogCta.addressBarPosition.bottomOmnibarToggleCheck.setImageResource(bottomButton.checkRes)

            // Configure split option
            val splitButton = OmnibarTypeToggleButton.Split(
                isActive = selectedOption == OmnibarType.SPLIT,
                isLightMode = isLightMode,
            )
            binding.daxDialogCta.addressBarPosition.splitOmnibarToggleImage.setImageResource(splitButton.imageRes)
            binding.daxDialogCta.addressBarPosition.splitOmnibarToggleCheck.setImageResource(splitButton.checkRes)
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setBackgroundRes(
            if (appTheme.isLightModeEnabled()) {
                CommonR.drawable.onboarding_background_bitmap_light
            } else {
                CommonR.drawable.onboarding_background_bitmap_dark
            },
        )

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

    private fun configureDaxCta(
        onboardingDialogType: PreOnboardingDialogType,
        showSplitOption: Boolean = false,
        showDuckAiCopy: Boolean = false,
    ) {
        context?.let {
            var afterAnimation: () -> Unit = {}
            viewModel.onDialogShown(onboardingDialogType)
            when (onboardingDialogType) {
                SYNC_RESTORE -> {
                    binding.daxDialogCta.root.show()
                    binding.daxDialogCta.progressBarText.gone()
                    binding.daxDialogCta.progressBar.gone()
                    binding.daxDialogCta.descriptionCta.show()
                    binding.daxDialogCta.descriptionCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.secondaryCta.show()
                    binding.daxDialogCta.daxDialogContentImage.gone()

                    val ctaText = it.getString(R.string.syncRestoreDialogTitle)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    val descriptionText = it.getString(R.string.syncRestoreDialogDescription)
                    binding.daxDialogCta.descriptionCta.text = descriptionText.html(it)
                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        binding.daxDialogCta.descriptionCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.primaryCta.setText(R.string.syncRestoreDialogPrimaryCta)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(SYNC_RESTORE) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.secondaryCta.text = it.getString(R.string.syncRestoreDialogSecondaryCta)
                        binding.daxDialogCta.secondaryCta.setOnClickListener { viewModel.onSecondaryCtaClicked(SYNC_RESTORE) }
                        binding.daxDialogCta.secondaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                    scheduleTypingAnimation(ctaText) { afterAnimation() }
                }

                INITIAL_REINSTALL_USER -> {
                    binding.daxDialogCta.root.show()
                    binding.daxDialogCta.progressBarText.gone()
                    binding.daxDialogCta.progressBar.gone()
                    binding.daxDialogCta.descriptionCta.gone()
                    binding.daxDialogCta.secondaryCta.show()

                    val titleRes = if (showDuckAiCopy) R.string.preOnboardingDaxDialog1TitleDuckAi else R.string.preOnboardingDaxDialog1Title
                    val ctaText = it.getString(titleRes)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.daxDialogContentImage.gone()
                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        binding.daxDialogCta.primaryCta.setText(
                            if (showDuckAiCopy) R.string.preOnboardingDaxDialog1ButtonDuckAi else R.string.preOnboardingDaxDialog1Button,
                        )
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

                    val titleRes = if (showDuckAiCopy) R.string.preOnboardingDaxDialog1TitleDuckAi else R.string.preOnboardingDaxDialog1Title
                    val ctaText = it.getString(titleRes)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.daxDialogContentImage.gone()
                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        binding.daxDialogCta.primaryCta.setText(
                            if (showDuckAiCopy) R.string.preOnboardingDaxDialog1ButtonDuckAi else R.string.preOnboardingDaxDialog1Button,
                        )
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
                    val maxPages = viewModel.getMaxPageCount()
                    binding.daxDialogCta.progressBarText.text = "1 / $maxPages"
                    binding.daxDialogCta.progressBar.show()
                    binding.daxDialogCta.progressBar.max = maxPages
                    binding.daxDialogCta.progressBar.progress = 1
                    val ctaText = it.getString(R.string.preOnboardingDaxDialog2Title)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA

                    binding.daxDialogCta.comparisonChart.root.isVisible = !showDuckAiCopy
                    binding.daxDialogCta.comparisonChart.root.alpha = MIN_ALPHA
                    binding.daxDialogCta.comparisonChartWithDuckAi.root.isVisible = showDuckAiCopy
                    binding.daxDialogCta.comparisonChartWithDuckAi.root.alpha = MIN_ALPHA

                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingDaxDialog2Button)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(COMPARISON_CHART) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        val comparisonChart = if (showDuckAiCopy) {
                            binding.daxDialogCta.comparisonChartWithDuckAi
                        } else {
                            binding.daxDialogCta.comparisonChart
                        }
                        comparisonChart.root.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
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
                    binding.daxDialogCta.comparisonChartWithDuckAi.root.gone()
                    TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
                    binding.daxDialogCta.progressBarText.show()
                    val maxPages = viewModel.getMaxPageCount()
                    binding.daxDialogCta.progressBarText.text = "2 / $maxPages"
                    binding.daxDialogCta.progressBar.show()
                    binding.daxDialogCta.progressBar.max = maxPages
                    binding.daxDialogCta.progressBar.progress = 2
                    val ctaText = it.getString(R.string.preOnboardingAddressBarTitle)
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.addressBarPosition.root.show()
                    binding.daxDialogCta.addressBarPosition.root.alpha = MIN_ALPHA

                    // Show or hide split option based on feature toggle
                    if (showSplitOption) {
                        binding.daxDialogCta.addressBarPosition.splitOmnibarContainer.show()
                    } else {
                        binding.daxDialogCta.addressBarPosition.splitOmnibarContainer.gone()
                    }

                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        setAddressBarPositionOptions(OmnibarType.SINGLE_TOP) // Default to top
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingAddressBarOkButton)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(ADDRESS_BAR_POSITION) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.addressBarPosition.topOmnibarContainer.setOnClickListener {
                            viewModel.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_TOP)
                        }
                        binding.daxDialogCta.addressBarPosition.bottomOmnibarContainer.setOnClickListener {
                            viewModel.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_BOTTOM)
                        }
                        if (showSplitOption) {
                            binding.daxDialogCta.addressBarPosition.splitOmnibarContainer.setOnClickListener {
                                viewModel.onAddressBarPositionOptionSelected(OmnibarType.SPLIT)
                            }
                        }
                        binding.daxDialogCta.addressBarPosition.root.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }

                    scheduleTypingAnimation(ctaText) { afterAnimation() }
                }

                INPUT_SCREEN -> {
                    binding.daxDialogCta.descriptionCta.gone()
                    binding.daxDialogCta.secondaryCta.gone()
                    binding.daxDialogCta.dialogTextCta.text = ""
                    binding.daxDialogCta.comparisonChart.root.gone()
                    binding.daxDialogCta.addressBarPosition.root.gone()
                    TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())
                    binding.daxDialogCta.progressBarText.show()
                    val maxPages = viewModel.getMaxPageCount()
                    binding.daxDialogCta.progressBarText.text = "3 / $maxPages"
                    binding.daxDialogCta.progressBar.show()
                    binding.daxDialogCta.progressBar.max = maxPages
                    binding.daxDialogCta.progressBar.progress = 3
                    val ctaText = it.getString(
                        if (showDuckAiCopy) R.string.preOnboardingInputScreenTitleUpdated else R.string.preOnboardingInputScreenTitle,
                    )
                    binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)

                    binding.daxDialogCta.duckAiInputScreenToggleWithoutAiCaption.setText(
                        if (showDuckAiCopy) {
                            DuckChatR.string.input_screen_user_pref_without_ai_updated
                        } else {
                            DuckChatR.string.input_screen_user_pref_without_ai
                        },
                    )

                    binding.daxDialogCta.duckAiInputScreenToggleWithAiCaption.setText(
                        if (showDuckAiCopy) {
                            DuckChatR.string.input_screen_user_pref_with_ai_updated
                        } else {
                            DuckChatR.string.input_screen_user_pref_with_ai
                        },
                    )

                    binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
                    binding.daxDialogCta.duckAiInputScreenToggleContainer.show()
                    binding.daxDialogCta.duckAiInputScreenToggleContainer.alpha = MIN_ALPHA

                    val isLightMode = appTheme.isLightModeEnabled()
                    updateAiChatToggleState(binding, isLightMode, withAi = true)
                    viewModel.onInputScreenOptionSelected(withAi = true)

                    binding.daxDialogCta.duckAiInputScreenWithoutAiContainer.setOnClickListener {
                        updateAiChatToggleState(binding, isLightMode, withAi = false)
                        viewModel.onInputScreenOptionSelected(withAi = false)
                    }
                    binding.daxDialogCta.duckAiInputScreenWithAiContainer.setOnClickListener {
                        updateAiChatToggleState(binding, isLightMode, withAi = true)
                        viewModel.onInputScreenOptionSelected(withAi = true)
                    }

                    val descriptionText = it.getString(R.string.preOnboardingInputScreenDescription)
                    binding.daxDialogCta.duckAiInputScreenToggleDescription.text = descriptionText.html(it)
                    binding.daxDialogCta.duckAiInputScreenToggleDescription.show()
                    binding.daxDialogCta.duckAiInputScreenToggleDescription.alpha = MIN_ALPHA

                    afterAnimation = {
                        binding.daxDialogCta.dialogTextCta.finishAnimation()
                        binding.daxDialogCta.primaryCta.text = it.getString(R.string.preOnboardingInputScreenButton)
                        binding.daxDialogCta.primaryCta.setOnClickListener { viewModel.onPrimaryCtaClicked(INPUT_SCREEN) }
                        binding.daxDialogCta.primaryCta.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.duckAiInputScreenToggleContainer.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                        binding.daxDialogCta.duckAiInputScreenToggleDescription.animate().alpha(MAX_ALPHA).duration = ANIMATION_DURATION
                    }
                    scheduleTypingAnimation(ctaText) { afterAnimation() }
                }

                INPUT_SCREEN_PREVIEW -> return@let // handled by configureInputScreenPreviewDialog()
            }
            binding.sceneBg.setOnClickListener { afterAnimation() }
            binding.daxDialogCta.cardContainer.setOnClickListener { afterAnimation() }
        }
    }

    private fun configureInputScreenPreviewDialog(
        searchSuggestions: List<DaxDialogIntroOption>,
        chatSuggestions: List<DaxDialogIntroOption>,
        defaultInputMode: InputMode,
    ) {
        val ctx = context ?: return
        viewModel.onDialogShown(INPUT_SCREEN_PREVIEW)

        TransitionManager.beginDelayedTransition(binding.longDescriptionContainer, AutoTransition())
        binding.daxDialogCta.descriptionCta.gone()
        binding.daxDialogCta.primaryCta.gone()
        binding.daxDialogCta.secondaryCta.gone()
        binding.daxDialogCta.dialogTextCta.text = ""
        binding.daxDialogCta.comparisonChart.root.gone()
        binding.daxDialogCta.comparisonChartWithDuckAi.root.gone()
        binding.daxDialogCta.addressBarPosition.root.gone()
        binding.daxDialogCta.duckAiInputScreenToggleContainer.gone()
        binding.daxDialogCta.duckAiInputScreenToggleDescription.gone()
        binding.daxDialogCta.progressBarText.gone()
        binding.daxDialogCta.progressBar.gone()
        binding.daxDialogCta.logo.gone()

        binding.daxDialogCta.cardView.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = 0 }
        binding.daxDialogCta.root.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = 20.toPx() }

        val ctaText = ctx.getString(R.string.preOnboardingInputModeDemoTitle)
        binding.daxDialogCta.hiddenTextCta.text = ctaText.html(ctx)
        binding.daxDialogCta.primaryCta.alpha = MIN_ALPHA
        binding.daxDialogCta.inputScreenPreview.root.show()
        binding.daxDialogCta.inputScreenPreview.root.alpha = MIN_ALPHA

        when (defaultInputMode) {
            CHAT -> {
                setInputScreenPreviewInputMode(CHAT, chatSuggestions)
                binding.daxDialogCta.inputScreenPreview.inputModeToggle.getTabAt(1)?.select()
            }
            SEARCH -> {
                setInputScreenPreviewInputMode(SEARCH, searchSuggestions)
            }
        }

        binding.daxDialogCta.inputScreenPreview.inputModeToggle.addOnTabSelectedListener(
            object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    TransitionManager.beginDelayedTransition(binding.daxDialogCta.cardView, AutoTransition())

                    if (tab.position == 0) {
                        setInputScreenPreviewInputMode(SEARCH, searchSuggestions)
                    } else {
                        setInputScreenPreviewInputMode(CHAT, chatSuggestions)
                    }
                }
                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            },
        )

        val afterAnimation = {
            binding.daxDialogCta.dialogTextCta.finishAnimation()
            if (binding.daxDialogCta.inputScreenPreview.root.alpha == 0f) {
                binding.daxDialogCta.inputScreenPreview.root.animate()
                    .alpha(MAX_ALPHA)
                    .setDuration(ANIMATION_DURATION)
                    .withEndAction {
                        if (view == null) return@withEndAction

                        binding.daxDialogCta.inputScreenPreview.inputText.apply {
                            isFocusable = true
                            isFocusableInTouchMode = true

                            if (resources.configuration.screenHeightDp >= MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP) {
                                post {
                                    if (view == null) return@post
                                    activity?.showKeyboard(binding.daxDialogCta.inputScreenPreview.inputText)
                                }
                            }
                        }

                        val buttons = listOf(
                            binding.daxDialogCta.inputScreenPreview.suggestion1,
                            binding.daxDialogCta.inputScreenPreview.suggestion2,
                            binding.daxDialogCta.inputScreenPreview.suggestion3,
                        )

                        fun animateButton(index: Int) {
                            if (view == null) return

                            if (index < buttons.size) {
                                buttons[index].alpha = MIN_ALPHA
                                buttons[index].isVisible = true

                                TransitionManager.beginDelayedTransition(
                                    binding.daxDialogCta.cardView,
                                    AutoTransition(),
                                )

                                buttons[index].animate()
                                    .alpha(MAX_ALPHA)
                                    .setDuration(INPUT_SCREEN_PREVIEW_SUGGESTION_ANIMATION_DURATION)
                                    .withEndAction { animateButton(index + 1) }
                                    .start()
                            }
                        }

                        viewLifecycleOwner.lifecycleScope.launch {
                            delay(INPUT_SCREEN_PREVIEW_SUGGESTIONS_ANIMATION_DELAY)
                            animateButton(0)
                        }
                    }
                    .start()
            }
        }
        scheduleTypingAnimation(ctaText) { afterAnimation() }
        binding.sceneBg.setOnClickListener { afterAnimation() }
        binding.daxDialogCta.cardContainer.setOnClickListener { afterAnimation() }
    }

    private fun setInputScreenPreviewInputMode(
        inputMode: InputMode,
        suggestions: List<DaxDialogIntroOption>,
    ) {
        currentInputMode = inputMode
        val inputScreenPreviewBinding = binding.daxDialogCta.inputScreenPreview

        val buttons = listOf(inputScreenPreviewBinding.suggestion1, inputScreenPreviewBinding.suggestion2, inputScreenPreviewBinding.suggestion3)
        buttons.forEachIndexed { index, button ->
            suggestions[index].setOptionView(button)
            button.setOnClickListener {
                viewModel.onInputModeDemoQuerySubmitted(suggestions[index].link, isChat = inputMode == CHAT)
            }
        }

        inputScreenPreviewBinding.inputModeDemoActionIcon.setOnClickListener {
            val query = inputScreenPreviewBinding.inputText.text?.toString().orEmpty().trim()
            if (query.isNotEmpty()) {
                viewModel.onInputModeDemoQuerySubmitted(query, isChat = currentInputMode == CHAT)
            }
        }

        when (inputMode) {
            SEARCH -> {
                inputScreenPreviewBinding.inputText.minLines = 1
                inputScreenPreviewBinding.inputText.maxLines = 1
                inputScreenPreviewBinding.inputText.setHint(R.string.preOnboardingInputModeDemoSearchHint)
                inputScreenPreviewBinding.inputModeDemoActionIcon.setImageResource(CommonR.drawable.ic_find_search_24)
            }
            CHAT -> {
                inputScreenPreviewBinding.inputText.minLines = 3
                inputScreenPreviewBinding.inputText.maxLines = 3
                inputScreenPreviewBinding.inputText.setHint(R.string.preOnboardingInputModeDemoChatHint)
                inputScreenPreviewBinding.inputModeDemoActionIcon.setImageResource(CommonR.drawable.ic_arrow_right_24)
            }
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

    private fun updateAiChatToggleState(
        binding: ContentOnboardingWelcomePageBinding,
        isLightMode: Boolean,
        withAi: Boolean,
    ) {
        val withoutAiImageRes = when {
            !withAi && isLightMode -> com.duckduckgo.duckchat.impl.R.drawable.searchbox_withoutai_active
            !withAi && !isLightMode -> com.duckduckgo.duckchat.impl.R.drawable.searchbox_withoutai_active_dark
            withAi && isLightMode -> com.duckduckgo.duckchat.impl.R.drawable.searchbox_withoutai_inactive
            else -> com.duckduckgo.duckchat.impl.R.drawable.searchbox_withoutai_inactive_dark
        }
        val withAiImageRes = when {
            withAi && isLightMode -> com.duckduckgo.duckchat.impl.R.drawable.searchbox_withai_active
            withAi && !isLightMode -> com.duckduckgo.duckchat.impl.R.drawable.searchbox_withai_active_dark
            !withAi && isLightMode -> com.duckduckgo.duckchat.impl.R.drawable.searchbox_withai_inactive
            else -> com.duckduckgo.duckchat.impl.R.drawable.searchbox_withai_inactive_dark
        }

        binding.daxDialogCta.duckAiInputScreenToggleWithoutAiImage.setImageResource(withoutAiImageRes)
        binding.daxDialogCta.duckAiInputScreenToggleWithAiImage.setImageResource(withAiImageRes)

        val withoutAiCheckRes = if (!withAi) {
            CommonR.drawable.ic_check_accent_24
        } else {
            CommonR.drawable.ic_shape_circle_24
        }
        val withAiCheckRes = if (withAi) {
            CommonR.drawable.ic_check_accent_24
        } else {
            CommonR.drawable.ic_shape_circle_24
        }

        binding.daxDialogCta.duckAiInputScreenToggleWithoutAiCheck.setImageResource(withoutAiCheckRes)
        binding.daxDialogCta.duckAiInputScreenToggleWithAiCheck.setImageResource(withAiCheckRes)
    }

    private sealed class OmnibarTypeToggleButton(
        isActive: Boolean,
    ) {
        abstract val imageRes: Int

        val checkRes: Int =
            if (isActive) {
                CommonR.drawable.ic_check_accent_24
            } else {
                CommonR.drawable.ic_shape_circle_disabled_24
            }

        class Top(
            isActive: Boolean,
            isLightMode: Boolean,
        ) : OmnibarTypeToggleButton(isActive) {
            override val imageRes: Int =
                when {
                    isActive && isLightMode -> R.drawable.mobile_toolbar_top_selected_light
                    isActive && !isLightMode -> R.drawable.mobile_toolbar_top_selected_dark
                    !isActive && isLightMode -> R.drawable.mobile_toolbar_top_unselected_light
                    else -> R.drawable.mobile_toolbar_top_unselected_dark
                }
        }

        class Bottom(
            isActive: Boolean,
            isLightMode: Boolean,
        ) : OmnibarTypeToggleButton(isActive) {
            override val imageRes: Int =
                when {
                    isActive && isLightMode -> R.drawable.mobile_toolbar_bottom_selected_light
                    isActive && !isLightMode -> R.drawable.mobile_toolbar_bottom_selected_dark
                    !isActive && isLightMode -> R.drawable.mobile_toolbar_bottom_unselected_light
                    else -> R.drawable.mobile_toolbar_bottom_unselected_dark
                }
        }

        class Split(
            isActive: Boolean,
            isLightMode: Boolean,
        ) : OmnibarTypeToggleButton(isActive) {
            override val imageRes: Int =
                when {
                    isActive && isLightMode -> R.drawable.mobile_toolbar_split_selected_light
                    isActive && !isLightMode -> R.drawable.mobile_toolbar_split_selected_dark
                    !isActive && isLightMode -> R.drawable.mobile_toolbar_split_unselected_light
                    else -> R.drawable.mobile_toolbar_split_unselected_dark
                }
        }
    }

    private enum class InputMode { SEARCH, CHAT }

    companion object {
        private const val MIN_ALPHA = 0f
        private const val MAX_ALPHA = 1f
        private const val ANIMATION_DURATION = 400L
        private const val ANIMATION_DELAY = 1400L
        private const val ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED = 800L

        private const val MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP = 600
        private const val INPUT_SCREEN_PREVIEW_SUGGESTION_ANIMATION_DURATION = 500L
        private const val INPUT_SCREEN_PREVIEW_SUGGESTIONS_ANIMATION_DELAY = 500L
        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}
