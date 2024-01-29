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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@InjectWith(FragmentScope::class)
class WelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome) {

    @Inject
    lateinit var viewModelFactory: WelcomePageViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // In case of screen rotation while the notifications permissions prompt is shown on screen a DENY result is received
        // as the dialog gets automatically dismissed and recreated. Proceed with the welcome animation only if the dialog is not
        // displayed on top of the onboarding.
        if (view?.windowVisibility == View.VISIBLE) {
            // Nothing to do at this point with the result. Proceed with the welcome animation.
            scheduleTypingAnimation()
        }
    }

    private var ctaText: String = ""
    private var welcomeAnimation: ViewPropertyAnimatorCompat? = null
    private var typingAnimation: ViewPropertyAnimatorCompat? = null
    private var welcomeAnimationFinished = false

    // we use replay = 0 because we don't want to emit the last value upon subscription
    private val events = MutableSharedFlow<WelcomePageView.Event>(replay = 0, extraBufferCapacity = 1)

    private val welcomePageViewModel: WelcomePageViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[WelcomePageViewModel::class.java]
    }

    private val binding: ContentOnboardingWelcomeBinding by viewBinding()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        configureDaxCta()
        scheduleWelcomeAnimation()
        setSkipAnimationListener()

        lifecycleScope.launch {
            events
                .flatMapLatest { welcomePageViewModel.reduce(it) }
                .flowOn(dispatcherProvider.io())
                .collect(::render)
        }
    }

    private fun scheduleWelcomeAnimation() {
        welcomeAnimation = ViewCompat.animate(binding.welcomeContent as View)
            .alpha(MIN_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .setStartDelay(ANIMATION_DELAY)
            .withEndAction {
                requestNotificationsPermissions()
            }
    }

    private fun requestNotificationsPermissions() {
        if (appBuildConfig.sdkInt >= android.os.Build.VERSION_CODES.TIRAMISU) {
            event(WelcomePageView.Event.OnNotificationPermissionsRequested)
        } else {
            scheduleTypingAnimation()
        }
    }

    @SuppressLint("InlinedApi")
    private fun showNotificationsPermissionsPrompt() {
        if (appBuildConfig.sdkInt >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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

            WelcomePageView.State.ShowWelcomeAnimation -> scheduleTypingAnimation()
            WelcomePageView.State.ShowNotificationsPermissionsPrompt -> showNotificationsPermissionsPrompt()
        }
    }

    private fun event(event: WelcomePageView.Event) {
        lifecycleScope.launch(dispatcherProvider.io()) {
            events.emit(event)
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
                scheduleTypingAnimation()
            }
            welcomeAnimationFinished = true
        }
    }

    private fun scheduleTypingAnimation() {
        typingAnimation = ViewCompat.animate(binding.daxDialogCta.daxCtaContainer)
            .alpha(MAX_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                welcomeAnimationFinished = true
                binding.daxDialogCta.dialogTextCta.startTypingAnimation(ctaText)
                setPrimaryCtaListenerAfterWelcomeAlphaAnimation()
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
        private const val ANIMATION_DURATION = 1200L
        private const val ANIMATION_DELAY = 1800L
        private const val ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED = 800L

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}
