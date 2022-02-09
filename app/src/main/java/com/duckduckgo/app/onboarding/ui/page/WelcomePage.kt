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

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.view.html
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.content_onboarding_welcome.*
import kotlinx.android.synthetic.main.include_dax_dialog_cta.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@ExperimentalCoroutinesApi
class WelcomePage : OnboardingPageFragment() {

    @Inject
    lateinit var viewModelFactory: WelcomePageViewModelFactory

    private var ctaText: String = ""
    private var welcomeAnimation: ViewPropertyAnimatorCompat? = null
    private var typingAnimation: ViewPropertyAnimatorCompat? = null
    private var welcomeAnimationFinished = false

    // we use a BroadcastChannel because we don't want to emit the last value upon subscription
    private val events = BroadcastChannel<WelcomePageView.Event>(1)

    private val welcomePageViewModel: WelcomePageViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(WelcomePageViewModel::class.java)
    }

    override fun layoutResource(): Int = R.layout.content_onboarding_welcome

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        configureDaxCta()
        buildScreenContent()
        scheduleWelcomeAnimation()
        setSkipAnimationListener()

        lifecycleScope.launch {
            events.asFlow()
                .flatMapLatest { welcomePageViewModel.reduce(it) }
                .collect(::render)
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    private fun buildScreenContent() {
        lifecycleScope.launch {
            welcomePageViewModel.screenContent.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect {
                    when (it) {
                        is WelcomePageViewModel.ViewState.ContinueWithoutPrivacyTipsState ->
                            renderNewOrReturningUsers(R.string.returningUsersContinuesWithoutPrivacyTipsButtonLabel)
                        is WelcomePageViewModel.ViewState.SkipTutorialState ->
                            renderNewOrReturningUsers(R.string.returningUsersSkipTutorialButtonLabel)
                        is WelcomePageViewModel.ViewState.DefaultOnboardingState ->
                            renterDefaultOnboarding()
                    }
                }
        }
    }

    private fun renderNewOrReturningUsers(@StringRes returningUserButtonRes: Int) {
        returningUserCta.visibility = VISIBLE
        returningUserCta.text = getString(returningUserButtonRes)
        returningUserCta.setOnClickListener { event(WelcomePageView.Event.OnReturningUserClicked) }
        ctaText = getString(R.string.onboardingDaxText)
        hiddenTextCta.text = ctaText.html(requireContext())
        primaryCta.text = getString(R.string.returningUsersLetsDoItButtonLabel)
    }

    private fun renterDefaultOnboarding() {
        returningUserCta.visibility = GONE
        ctaText = getString(R.string.onboardingDaxText)
        hiddenTextCta.text = ctaText.html(requireContext())
        primaryCta.text = getString(R.string.onboardingLetsDoItButton)
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
        data: Intent?
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            decorView.systemUiVisibility += View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            statusBarColor = Color.TRANSPARENT
        }
        ViewCompat.requestApplyInsets(longDescriptionContainer)
    }

    private fun configureDaxCta() {
        context?.let {
            ctaText = it.getString(R.string.onboardingDaxText)
            hiddenTextCta.text = ctaText.html(it)
            dialogTextCta.textInDialog = ctaText.html(it)
            dialogTextCta.setTextColor(ContextCompat.getColor(it, R.color.grayishBrown))
            cardView.backgroundTintList = ContextCompat.getColorStateList(it, R.color.white)
        }
        triangle.setImageResource(R.drawable.ic_triangle_bubble_white)
    }

    private fun setSkipAnimationListener() {
        longDescriptionContainer.setOnClickListener {
            if (dialogTextCta.hasAnimationStarted()) {
                finishTypingAnimation()
            } else if (!welcomeAnimationFinished) {
                welcomeAnimation?.cancel()
                scheduleWelcomeAnimation(0L)
            }
            welcomeAnimationFinished = true
        }
    }

    private fun scheduleWelcomeAnimation(startDelay: Long = ANIMATION_DELAY) {
        welcomeAnimation = ViewCompat.animate(welcomeContent as View)
            .alpha(MIN_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .setStartDelay(startDelay)
            .withEndAction {
                typingAnimation = ViewCompat.animate(daxCtaContainer)
                    .alpha(MAX_ALPHA)
                    .setDuration(ANIMATION_DURATION)
                    .withEndAction {
                        welcomeAnimationFinished = true
                        dialogTextCta.startTypingAnimation(ctaText)
                        setPrimaryCtaListenerAfterWelcomeAlphaAnimation()
                    }
            }
    }

    private fun finishTypingAnimation() {
        welcomeAnimation?.cancel()
        dialogTextCta.finishAnimation()
        setPrimaryCtaListenerAfterWelcomeAlphaAnimation()
    }

    private fun setPrimaryCtaListenerAfterWelcomeAlphaAnimation() {
        primaryCta.setOnClickListener { event(WelcomePageView.Event.OnPrimaryCtaClicked) }
    }

    companion object {
        private const val MIN_ALPHA = 0f
        private const val MAX_ALPHA = 1f
        private const val ANIMATION_DURATION = 400L
        private const val ANIMATION_DELAY = 1400L

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}
