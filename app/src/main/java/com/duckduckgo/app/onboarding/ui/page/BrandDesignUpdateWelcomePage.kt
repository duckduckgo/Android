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
import android.os.Build
import android.os.Bundle
import android.view.View
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.animation.PathInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewPropertyAnimatorCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INPUT_SCREEN
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class BrandDesignUpdateWelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page_update) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var appTheme: AppTheme

    private val binding: ContentOnboardingWelcomePageUpdateBinding by viewBinding()
    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[BrandDesignUpdatePageViewModel::class.java]
    }

    private var welcomeAnimation: ViewPropertyAnimatorCompat? = null
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
        requireActivity().apply {
            enableEdgeToEdge()
        }
    }

    private fun setAddressBarPositionOptions(selectedOption: OmnibarType) {
        context?.let { ctx ->
            // TODO
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                state.currentDialog?.let { dialogType ->
                    configureDaxCta(dialogType, state.showSplitOption)
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    is BrandDesignUpdatePageViewModel.Command.ShowDefaultBrowserDialog -> showDefaultBrowserDialog(command.intent)
                    is BrandDesignUpdatePageViewModel.Command.Finish -> onContinuePressed()
                    is BrandDesignUpdatePageViewModel.Command.OnboardingSkipped -> onSkipPressed()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        requestNotificationsPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        welcomeAnimation?.cancel()
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
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.notificationRuntimePermissionRequested()
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleWelcomeAnimation()
        }
    }

    private fun configureDaxCta(
        onboardingDialogType: PreOnboardingDialogType,
        showSplitOption: Boolean = false
    ) {
        context?.let {
            // var afterAnimation: () -> Unit = {}
            when (onboardingDialogType) {
                INITIAL_REINSTALL_USER -> {
                    // TODO
                }

                INITIAL -> {
                    // TODO
                }

                COMPARISON_CHART -> {
                    // TODO
                }

                SKIP_ONBOARDING_OPTION -> {
                    // TODO
                }

                ADDRESS_BAR_POSITION -> {
                    // TODO
                }

                INPUT_SCREEN -> {
                    // TODO
                }
            }
        }
    }

    private fun scheduleWelcomeAnimation(startDelay: Long = ANIMATION_DELAY) {
        // TODO
    }

    private fun showDefaultBrowserDialog(intent: Intent) {
        startActivityForResult(intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
    }

    private fun updateAiChatToggleState(
        binding: ContentOnboardingWelcomePageUpdateBinding,
        isLightMode: Boolean,
        withAi: Boolean,
    ) {
        // TODO
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

    companion object {
        private const val MIN_ALPHA = 0f
        private const val MAX_ALPHA = 1f
        private const val ANIMATION_DURATION = 400L
        private const val ANIMATION_DELAY = 1400L
        private const val ANIMATION_DELAY_AFTER_NOTIFICATIONS_PERMISSIONS_HANDLED = 800L

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}
