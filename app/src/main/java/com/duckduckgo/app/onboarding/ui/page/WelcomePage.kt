/*
 * Copyright (c) 2019 DuckDuckGo
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
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomeBinding
import com.duckduckgo.app.global.extensions.html
import com.duckduckgo.app.onboarding.ui.customisationexperiment.DDGFeatureOnboardingOption
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import javax.inject.Inject
import kotlinx.android.synthetic.main.include_dax_multiselect_dialog_cta.view.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@InjectWith(FragmentScope::class)
class WelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome) {

    @Inject
    lateinit var viewModelFactory: WelcomePageViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // In case of screen rotation while the notifications permissions prompt is shown on screen a DENY result is received
        // as the dialog gets automatically dismissed and recreated. Proceed with the welcome animation only if the dialog is not
        // displayed on top of the onboarding.
        if (view?.windowVisibility == View.VISIBLE) {
            // Nothing to do at this point with the result. Proceed with the welcome animation.
            scheduleWelcomeAnimation(ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED)
        }
    }

    private var ctaText: String = ""
    private var welcomeAnimation: ViewPropertyAnimatorCompat? = null
    private var typingAnimation: ViewPropertyAnimatorCompat? = null
    private var welcomeAnimationFinished = false

    // we use a BroadcastChannel because we don't want to emit the last value upon subscription
    private val events = BroadcastChannel<WelcomePageView.Event>(1)

    private val welcomePageViewModel: WelcomePageViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(WelcomePageViewModel::class.java)
    }

    private val binding: ContentOnboardingWelcomeBinding by viewBinding()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        configureDaxCta()
        requestNotificationsPermissions()
        setSkipAnimationListener()

        lifecycleScope.launch {
            events.asFlow()
                .flatMapLatest { welcomePageViewModel.reduce(it) }
                .collect(::render)
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

    private fun render(state: WelcomePageView.State) {
        when (state) {
            WelcomePageView.State.Idle -> {}
            is WelcomePageView.State.ShowDefaultBrowserDialog -> {
                showDefaultBrowserDialog(state.intent)
            }
            WelcomePageView.State.Finish -> {
                onContinuePressed()
            }
            WelcomePageView.State.ShowFeatureOptionsCta -> showFeatureOptionsDialog()
        }
    }

    private fun showFeatureOptionsDialog() {
        binding.daxDialogCta.root.gone()
        binding.daxDialogMultiselectCta?.apply {
            root.show()
            root.animate().alpha(1.0f).duration = 1000
            primaryCta.setOnClickListener { getSelectedOptionsAndContinue() }
            secondaryCta.setOnClickListener { event(WelcomePageView.Event.OnSkipOptions) }
            optionPrivateSearch.setOnClickListener { showContinueButton() }
            optionTrackerBlocking.setOnClickListener { showContinueButton() }
            optionSmallerFootprint.setOnClickListener { showContinueButton() }
            optionFasterPageLoads.setOnClickListener { showContinueButton() }
            optionFewerAds.setOnClickListener { showContinueButton() }
            optionOneClickDataClearing.setOnClickListener { showContinueButton() }
        }
    }

    private fun getSelectedOptionsAndContinue() {
        var options: Map<DDGFeatureOnboardingOption, Boolean> = mapOf()
        binding.daxDialogMultiselectCta?.apply {
            options = mapOf(
                DDGFeatureOnboardingOption.PRIVATE_SEARCH to optionPrivateSearch.isItemSelected,
                DDGFeatureOnboardingOption.TRACKER_BLOCKING to optionTrackerBlocking.isItemSelected,
                DDGFeatureOnboardingOption.SMALLER_DIGITAL_FOOTPRINT to optionSmallerFootprint.isItemSelected,
                DDGFeatureOnboardingOption.FASTER_PAGE_LOADS to optionFasterPageLoads.isItemSelected,
                DDGFeatureOnboardingOption.FEWER_ADS to optionFewerAds.isItemSelected,
                DDGFeatureOnboardingOption.ONE_CLICK_DATA_CLEARING to optionOneClickDataClearing.isItemSelected,
            )
        }
        event(WelcomePageView.Event.OnContinueOptions(options))
    }

    private fun showContinueButton() {
        binding.daxDialogMultiselectCta?.apply {
            primaryCta.show()
            secondaryCta.gone()
        }
    }

    private fun event(event: WelcomePageView.Event) {
        lifecycleScope.launch {
            events.send(event)
        }
    }

    private fun showDefaultBrowserDialog(intent: Intent) {
        startActivityForResult(intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
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
            if (resultCode == RESULT_OK) {
                event(WelcomePageView.Event.OnDefaultBrowserSet)
            } else {
                event(WelcomePageView.Event.OnDefaultBrowserNotSet)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun applyFullScreenFlags() {
        activity?.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            decorView.systemUiVisibility += View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.BLACK
        }
        ViewCompat.requestApplyInsets(binding.longDescriptionContainer)
    }

    private fun configureDaxCta() {
        context?.let {
            ctaText = it.getString(R.string.onboardingDaxText)
            binding.daxDialogCta.hiddenTextCta.text = ctaText.html(it)
            binding.daxDialogCta.dialogTextCta.textInDialog = ctaText.html(it)
        }
    }

    private fun setSkipAnimationListener() {
        binding.longDescriptionContainer.setOnClickListener {
            if (binding.daxDialogCta.dialogTextCta.hasAnimationStarted()) {
                finishTypingAnimation()
            } else if (!welcomeAnimationFinished) {
                welcomeAnimation?.cancel()
                scheduleWelcomeAnimation(0L)
            }
            welcomeAnimationFinished = true
        }
    }

    private fun scheduleWelcomeAnimation(startDelay: Long = ANIMATION_DELAY) {
        welcomeAnimation = ViewCompat.animate(binding.welcomeContent as View)
            .alpha(MIN_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .setStartDelay(startDelay)
            .withEndAction {
                typingAnimation = ViewCompat.animate(binding.daxDialogCta.daxCtaContainer)
                    .alpha(MAX_ALPHA)
                    .setDuration(ANIMATION_DURATION)
                    .withEndAction {
                        welcomeAnimationFinished = true
                        binding.daxDialogCta.dialogTextCta.startTypingAnimation(ctaText)
                        setPrimaryCtaListenerAfterWelcomeAlphaAnimation()
                    }
            }
    }

    private fun finishTypingAnimation() {
        welcomeAnimation?.cancel()
        binding.daxDialogCta.dialogTextCta.finishAnimation()
        setPrimaryCtaListenerAfterWelcomeAlphaAnimation()
    }

    private fun setPrimaryCtaListenerAfterWelcomeAlphaAnimation() {
        binding.daxDialogCta.primaryCta.setOnClickListener { event(WelcomePageView.Event.OnPrimaryCtaClicked) }
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
