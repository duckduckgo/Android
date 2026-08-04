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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundAnimator
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.BackgroundControllerImpl
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.CardAnchorControllerImpl
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.CardAnchorResolver
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.CardArrowControllerImpl
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.CardStageImpl
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.ContentControllerImpl
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.DialogRenderEngine
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.EmbellishmentControllerImpl
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.StepIndicatorControllerImpl
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.isTablet
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class ConfigDrivenWelcomePageFragment : OnboardingPageFragment(R.layout.content_onboarding_welcome_page_update) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var deviceInfo: DeviceInfo

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var addWidgetLauncher: AddWidgetLauncher

    private val binding: ContentOnboardingWelcomePageUpdateBinding by viewBinding()
    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[ConfigDrivenOnboardingPageViewModel::class.java]
    }

    private var engine: DialogRenderEngine? = null

    /** Fed to the embellishment controller's fit corrector; kept in sync by the window-insets listener below. */
    private var cardBottomInsetPx = 0

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (view?.windowVisibility == View.VISIBLE) {
            viewModel.notificationPermissionFlowFinished(granted)
        }
    }

    private val defaultBrowserRoleManagerDialog = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDefaultBrowserSet()
        } else {
            viewModel.onDefaultBrowserNotSet()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().enableEdgeToEdge()
    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val themeRes = if (appTheme.isLightModeEnabled()) {
            CommonR.style.Theme_DuckDuckGo_Light_Onboarding
        } else {
            CommonR.style.Theme_DuckDuckGo_Dark_Onboarding
        }
        return inflater.cloneInContext(ContextThemeWrapper(inflater.context, themeRes))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.daxDialogCta.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }
            // Under adjustResize, systemBars().bottom already includes the keyboard height while the IME shows,
            // which would leave the card measuring against a gap that is about to disappear.
            if (!windowInsets.isVisible(WindowInsetsCompat.Type.ime())) {
                cardBottomInsetPx = insets.bottom + DIALOG_BOTTOM_INSET_GAP_DP.toPx()
            }
            windowInsets
        }

        val cardAnchor = CardAnchorControllerImpl(binding, CardAnchorResolver(deviceInfo.isTablet()))

        engine = DialogRenderEngine(
            content = ContentControllerImpl(
                binding = binding.daxDialogCta,
                contentValues = viewModel.contentValues,
                isLightMode = { appTheme.isLightModeEnabled() },
            ),
            cardStage = CardStageImpl(binding),
            background = BackgroundControllerImpl(
                OnboardingBackgroundAnimator(
                    backgroundPrimary = binding.backgroundPrimary,
                    backgroundSecondary = binding.backgroundSecondary,
                ),
            ),
            embellishments = EmbellishmentControllerImpl(
                binding = binding,
                // A decoration that stops fitting leaves the card anchored to a hidden view, so re-run the same
                // anchor rule the render applies, which drops the arrow's depth along with it.
                onDecorationHidden = { cardAnchor.apply(null) },
                cardBottomInsetPx = { cardBottomInsetPx },
            ),
            cardAnchor = cardAnchor,
            cardArrow = CardArrowControllerImpl(binding.daxDialogCta.cardView),
            stepIndicator = StepIndicatorControllerImpl(binding.daxDialogCta.stepIndicator),
            emit = viewModel::onEvent,
            execute = viewModel::onContentInteraction,
            // While an entrance runs the card container swallows its children's touches, so a tap anywhere on the
            // card lands on the tap-to-skip listener below instead of a picker consuming it.
            onAnimatingChanged = { animating -> binding.daxDialogCta.cardContainer.interceptChildTouches = animating },
        )

        binding.root.setOnClickListener { engine?.skipRunningAnimations() }
        binding.daxDialogCta.cardContainer.setOnClickListener { engine?.skipRunningAnimations() }

        viewModel.viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state -> if (state.config != null) renderConfig(state) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { command -> handleCommand(command) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /**
     * Temporary until the intro animators are implemented in the follow-up.
     */
    private fun settleIntroViews() {
        binding.logoAnimation.isVisible = false
        binding.welcomeTitle.alpha = 0f
        binding.duckAiIntroAnimation.isVisible = false
    }

    private fun renderConfig(state: ConfigDrivenOnboardingPageViewModel.ViewState) {
        val engine = engine ?: return
        settleIntroViews()

        // A retained view model emits before a recreated view has been laid out, and the decoration fit
        // check measures the root's height, so measuring at 0 would hide the decoration for good.
        if (!binding.root.isLaidOut) {
            binding.root.doOnLayout { renderConfig(viewModel.viewState.value) }
            return
        }

        val stepId = state.stepId ?: return
        val config = state.config ?: return
        engine.render(stepId, config, state.animateEntry)
        viewModel.onDialogRendered(stepId)
    }

    private fun handleCommand(command: ConfigDrivenOnboardingPageViewModel.Command) {
        when (command) {
            ConfigDrivenOnboardingPageViewModel.Command.RequestNotificationPermissions -> requestNotificationsPermissions()
            is ConfigDrivenOnboardingPageViewModel.Command.ShowDefaultBrowserDialog ->
                defaultBrowserRoleManagerDialog.launch(command.intent)
            ConfigDrivenOnboardingPageViewModel.Command.LaunchAddWidgetPrompt ->
                addWidgetLauncher.launchAddWidget(activity, simpleWidgetPrompt = true)
            ConfigDrivenOnboardingPageViewModel.Command.Finish -> onContinuePressed()
            is ConfigDrivenOnboardingPageViewModel.Command.FinishAndSubmitSearchQuery ->
                (activity as? OnboardingActivity)?.finishAndSubmitSearchQuery(command.query)
            is ConfigDrivenOnboardingPageViewModel.Command.FinishAndSubmitChatPrompt ->
                (activity as? OnboardingActivity)?.finishAndSubmitChatPrompt(command.prompt)
            ConfigDrivenOnboardingPageViewModel.Command.OnboardingSkipped -> onSkipPressed()
            ConfigDrivenOnboardingPageViewModel.Command.HandOffToBrowserActivity ->
                (activity as? OnboardingActivity)?.handOffToBrowserActivity()
        }
    }

    @SuppressLint("InlinedApi")
    private fun requestNotificationsPermissions() {
        if (appBuildConfig.sdkInt >= 33) {
            viewModel.notificationRuntimePermissionRequested()
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.notificationPermissionFlowFinished(granted = null)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        engine?.release()
        engine = null
    }

    private companion object {
        const val DIALOG_BOTTOM_INSET_GAP_DP = 16
    }
}
