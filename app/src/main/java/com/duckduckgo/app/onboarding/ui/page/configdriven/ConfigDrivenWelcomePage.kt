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
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundAnimator
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.BackgroundController
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.CardAnchorController
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.DialogRenderEngine
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.EmbellishmentController
import com.duckduckgo.app.onboarding.ui.page.configdriven.engine.StepIndicatorController
import com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupAddressBarPositionBottomSheet
import com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupSearchOptionsBottomSheet
import com.duckduckgo.app.onboardingquicksetup.ui.RemoveWidgetInstructionsBottomSheet
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.isTablet
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Config-driven counterpart to `BrandDesignUpdateWelcomePage`, wiring [ConfigDrivenOnboardingPageViewModel] to
 * [DialogRenderEngine] and [OnboardingIntroChoreographer]. Constructed with no arguments (a later task's
 * `OnboardingPageBuilder` blueprint is the intended caller); all collaborators are built in [onViewCreated] from
 * the inflated [binding].
 *
 * Ported from legacy for this fragment alone (legacy keeps its own copy): command handling (:637-696),
 * activity-result launchers, bottom-sheet launch + `setFragmentResultListener` wiring (:2402-2442), tap-to-skip
 * (:543-544), `onResume` (:830-834), the edge-to-edge/theme setup (:210-226) and the window-insets listener that
 * feeds [EmbellishmentController]'s `cardBottomInsetPx` (:513-528) — the last two aren't in the task brief's
 * explicit line-range list but are needed for this fragment to render correctly on its own, so they're ported
 * here too (documented in `task-9-report.md`).
 */
@InjectWith(FragmentScope::class)
class ConfigDrivenWelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page_update) {

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
    private var intro: OnboardingIntroChoreographer? = null
    private var backgroundAnimator: OnboardingBackgroundAnimator? = null

    /** Fed to [EmbellishmentController]'s fit corrector; kept in sync by the window-insets listener below. */
    private var cardBottomInsetPx = 0

    /** True once this fragment instance has rendered its first [DialogConfig] — see [renderConfig]. */
    private var hasRenderedOnce = false

    /** True once this fragment instance itself has handled a [Command.PlayIntroAnimation] — see [renderConfig]. */
    private var introPlayedInThisView = false

    /** True for the duration of this instance's live intro animation; guards the render collector below. */
    private var introRunning = false

    /** A render deferred because [introRunning] was still true when its [ConfigDrivenOnboardingPageViewModel.ViewState] arrived. */
    private var pendingFirstRender: (() -> Unit)? = null

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

    private val quickSetupDefaultBrowserRoleManagerDialog =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onQuickSetupDefaultBrowserSet()
            } else {
                viewModel.onQuickSetupDefaultBrowserNotSet()
                // Legacy resets the switch's view directly (setCheckedSilently(false)); this writes the same
                // reset through the VM-owned store the binder observes, since the fragment never touches the
                // quick-setup include's views directly.
                currentQuickSetup()?.let { quickSetup ->
                    viewModel.contentValues.contentState(quickSetup).update { it.copy(defaultBrowserChecked = false) }
                }
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
        val contextThemeWrapper = ContextThemeWrapper(inflater.context, themeRes)
        return inflater.cloneInContext(contextThemeWrapper)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.daxDialogCta.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }
            // Under adjustResize, systemBars().bottom already includes the keyboard height while the IME shows.
            val trackImeInset = viewModel.viewState.value.config?.content is ContentConfig.InputScreenPreview
            if (!imeVisible || trackImeInset) {
                cardBottomInsetPx = insets.bottom + DIALOG_BOTTOM_INSET_GAP_DP.toPx()
            }
            windowInsets
        }

        val newBackgroundAnimator = OnboardingBackgroundAnimator(
            backgroundPrimary = binding.backgroundPrimary,
            backgroundSecondary = binding.backgroundSecondary,
        )
        backgroundAnimator = newBackgroundAnimator

        intro = OnboardingIntroChoreographer(binding)

        engine = DialogRenderEngine(
            binding = binding,
            contentBinder = ContentBinder(
                binding = binding.daxDialogCta,
                contentValues = viewModel.contentValues,
                isLightMode = { appTheme.isLightModeEnabled() },
            ),
            background = BackgroundController(newBackgroundAnimator),
            embellishments = EmbellishmentController(
                binding = binding,
                // Mirrors legacy's OnboardingDecorationFitCorrector construction (BrandDesignUpdateWelcomePage.kt:546-552):
                // the corrector already re-anchors the card's ConstraintLayout params itself when a decoration stops
                // fitting; this callback only resets the arrow depth, exactly as legacy's does.
                onDecorationHidden = { binding.daxDialogCta.cardView.setArrowDepthFraction(0f) },
                cardBottomInsetPx = { cardBottomInsetPx },
            ),
            cardAnchor = CardAnchorController(binding),
            stepIndicator = StepIndicatorController(binding.daxDialogCta.stepIndicator),
            isTablet = deviceInfo.isTablet(),
            emit = viewModel::onEvent,
            execute = viewModel::onContentInteraction,
        )

        binding.root.setOnClickListener { engine?.skipRunningAnimations() }
        binding.daxDialogCta.cardContainer.setOnClickListener { engine?.skipRunningAnimations() }

        registerBottomSheetResultListeners()

        viewModel.viewState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                when {
                    state.config == null -> Unit // intro not finished yet (or a command-only dialog is active)
                    !state.hasPlayedIntroAnimation -> Unit // wait for the intro command flow to finish
                    introRunning -> pendingFirstRender = { renderConfig(state) }
                    else -> renderConfig(state)
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { command -> handleCommand(command) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /**
     * Renders [state]'s [DialogConfig] through the [engine], handling the intro/first-dialog handshake:
     *  - If the intro played live in this instance ([introPlayedInThisView]), the first render plays the intro's
     *    outro fade first, then renders with `animate = true` — mirroring legacy's intro -> outro -> first-dialog
     *    chain.
     *  - Otherwise (mid-flow re-entry / rotation: this instance never played the intro) the intro-only views are
     *    snapped to their settled end state once, then the config renders with [ConfigDrivenOnboardingPageViewModel.ViewState.animateEntry].
     *
     * Every subsequent call renders directly with `state.animateEntry`. Note: since a config change always
     * creates a fresh engine (`previous == null`), [DialogRenderEngine.render]'s own "empty stage always
     * animates" policy means a rotation's first render animates its entrance regardless of `animateEntry` —
     * a known, spec-documented tension the engine itself flags, not something resolved here (see
     * `task-9-report.md`).
     */
    private fun renderConfig(state: ConfigDrivenOnboardingPageViewModel.ViewState) {
        val engine = engine ?: return
        val config = state.config ?: return

        if (!hasRenderedOnce) {
            hasRenderedOnce = true
            if (introPlayedInThisView) {
                intro?.playOutro { engine.render(config, animate = true) }
            } else {
                // Mirrors legacy's snapToIntroEndState() call at the top of showDialogWithoutAnimation
                // (BrandDesignUpdateWelcomePage.kt:1706): settle the intro-only views once before the first
                // real dialog renders, since this fresh instance's views start at their pre-intro XML defaults.
                intro?.snapToIntroEndState()
                engine.render(config, state.animateEntry)
            }
        } else {
            engine.render(config, state.animateEntry)
        }

        // One-shot: flips animateEntry off for this stepId so a later rotation re-collection of the VM's
        // (replayed, not recomputed) ViewState snaps instead of replaying this entrance again.
        state.stepId?.let { viewModel.onDialogRendered(it) }
    }

    private fun handleCommand(command: ConfigDrivenOnboardingPageViewModel.Command) {
        when (command) {
            is ConfigDrivenOnboardingPageViewModel.Command.PlayIntroAnimation -> {
                introRunning = true
                introPlayedInThisView = true
                binding.root.doOnLayout {
                    intro?.playIntroAnimation(withDuckAi = command.withDuckAi) {
                        introRunning = false
                        viewModel.onIntroAnimationFinished()
                        pendingFirstRender?.invoke()
                        pendingFirstRender = null
                    }
                }
            }
            ConfigDrivenOnboardingPageViewModel.Command.RequestNotificationPermissions -> requestNotificationsPermissions()
            is ConfigDrivenOnboardingPageViewModel.Command.ShowDefaultBrowserDialog ->
                defaultBrowserRoleManagerDialog.launch(command.intent)
            ConfigDrivenOnboardingPageViewModel.Command.OpenDefaultBrowserSystemSettings -> openDefaultBrowserSystemSettings()
            is ConfigDrivenOnboardingPageViewModel.Command.ShowQuickSetupDefaultBrowserDialog ->
                quickSetupDefaultBrowserRoleManagerDialog.launch(command.intent)
            ConfigDrivenOnboardingPageViewModel.Command.LaunchAddWidgetPrompt ->
                addWidgetLauncher.launchAddWidget(activity, simpleWidgetPrompt = true)
            ConfigDrivenOnboardingPageViewModel.Command.ShowRemoveWidgetBottomSheet ->
                RemoveWidgetInstructionsBottomSheet().show(childFragmentManager, RemoveWidgetInstructionsBottomSheet.TAG)
            is ConfigDrivenOnboardingPageViewModel.Command.ShowQuickSetupAddressBarPositionBottomSheet ->
                QuickSetupAddressBarPositionBottomSheet
                    .newInstance(initialSelection = command.initialSelection, showSplitOption = command.showSplitOption)
                    .show(childFragmentManager, QuickSetupAddressBarPositionBottomSheet.TAG)
            is ConfigDrivenOnboardingPageViewModel.Command.ShowQuickSetupSearchOptionsBottomSheet ->
                QuickSetupSearchOptionsBottomSheet
                    .newInstance(initialWithAi = command.initialWithAi)
                    .show(childFragmentManager, QuickSetupSearchOptionsBottomSheet.TAG)
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

    // Ported from legacy's registerQuickSetupBottomSheetResultListeners (BrandDesignUpdateWelcomePage.kt:2419-2442).
    // Registered once (not re-registered per dialog render, unlike legacy's per-render call site): setting a
    // listener under the same request key + viewLifecycleOwner is idempotent, and the quick-setup screen appears
    // at most once per plan run today.
    private fun registerBottomSheetResultListeners() {
        childFragmentManager.setFragmentResultListener(
            QuickSetupAddressBarPositionBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val selectedName = bundle.getString(QuickSetupAddressBarPositionBottomSheet.RESULT_KEY_SELECTED_POSITION)
                ?: return@setFragmentResultListener
            viewModel.onAddressBarBottomSheetResult(OmnibarType.valueOf(selectedName))
        }
        childFragmentManager.setFragmentResultListener(
            QuickSetupSearchOptionsBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            viewModel.onSearchOptionsBottomSheetResult(withAi = bundle.getBoolean(QuickSetupSearchOptionsBottomSheet.RESULT_KEY_WITH_AI))
        }
        childFragmentManager.setFragmentResultListener(
            RemoveWidgetInstructionsBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ ->
            // Legacy's counterpart here is checkWidgetAddedState() (Command.SyncAddWidgetSwitch), which has no
            // config-driven-VM equivalent; checkAddWidgetPromptResult() is the closest existing VM method, but it
            // only acts when the standalone add-widget prompt flow (LaunchAddWidgetPrompt) started it, so this is
            // a no-op for the quick-setup "remove widget" row. Known gap: see task-9-report.md.
            viewModel.checkAddWidgetPromptResult()
        }
    }

    private fun currentQuickSetup(): ContentConfig.QuickSetup? =
        viewModel.viewState.value.config?.content as? ContentConfig.QuickSetup

    @SuppressLint("InlinedApi")
    private fun requestNotificationsPermissions() {
        if (appBuildConfig.sdkInt >= 33) {
            viewModel.notificationRuntimePermissionRequested()
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.notificationPermissionFlowFinished(granted = null)
        }
    }

    private fun openDefaultBrowserSystemSettings() {
        try {
            startActivity(DefaultBrowserSystemSettings.intent())
        } catch (e: ActivityNotFoundException) {
            val errorMessage = getString(R.string.cannotLaunchDefaultAppSettings)
            logcat(WARN) { "$errorMessage: ${e.asLog()}" }
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
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
        intro?.releaseIntroAnimators()
        intro = null
        backgroundAnimator?.cancel()
        backgroundAnimator = null
    }

    private companion object {
        // Ported from BrandDesignUpdateWelcomePage.kt: DIALOG_BOTTOM_INSET_GAP_DP (:3085).
        const val DIALOG_BOTTOM_INSET_GAP_DP = 16
    }
}
