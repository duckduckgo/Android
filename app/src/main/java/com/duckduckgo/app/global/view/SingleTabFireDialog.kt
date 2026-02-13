/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.global.view

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.airbnb.lottie.RenderMode
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.SheetFireSingleTabBinding
import com.duckduckgo.app.global.view.SingleTabFireDialogViewModel.Command
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.setAndPropagateUpFitsSystemWindows
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.dataclearing.api.ClearDataAction
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.R as MaterialR

private const val ANIMATION_MAX_SPEED = 1.4f
private const val ANIMATION_SPEED_INCREMENT = 0.15f
private const val ARG_ORIGIN = "origin"

@InjectWith(FragmentScope::class)
class SingleTabFireDialog : BottomSheetDialogFragment(), FireDialog {
    @Inject lateinit var settingsDataStore: SettingsDataStore

    @Inject lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var clearDataAction: ClearDataAction

    @Inject lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel: SingleTabFireDialogViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SingleTabFireDialogViewModel::class.java]
    }

    private var _binding: SheetFireSingleTabBinding? = null
    private val binding get() = _binding!!

    private val accelerateAnimatorUpdateListener = object : ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            _binding?.fireAnimationView?.let {
                it.speed += ANIMATION_SPEED_INCREMENT
                if (it.speed > ANIMATION_MAX_SPEED) {
                    it.removeUpdateListener(this)
                }
            }
        }
    }

    private var animationEnabled = false
    private var canFinish = false

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), CommonR.style.Widget_DuckDuckGo_FireDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetFireSingleTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val originName = arguments?.getString(ARG_ORIGIN, "BROWSER") ?: "BROWSER"
        viewModel.setOrigin(FireDialogProvider.FireDialogOrigin.valueOf(originName))

        setupLayout()
        configureBottomSheet()
        observeViewModel()

        if (appBuildConfig.sdkInt == 26) {
            dialog?.window?.navigationBarColor = ContextCompat.getColor(requireContext(), CommonR.color.translucentDark)
        } else if (appBuildConfig.sdkInt in 27..<30) {
            dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }

        removeTopPadding()
        addBottomPaddingToButtons()

        animationEnabled = isAnimationEnabled()
        canFinish = !animationEnabled
        if (animationEnabled) {
            configureFireAnimationView()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onShow()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        viewModel.onCancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupLayout() {
        binding.deleteAllButton.setOnClickListener {
            hideDialog()
            viewModel.onDeleteAllClicked()
        }
        binding.deleteThisTabButton.setOnClickListener {
            hideDialog()
            viewModel.onDeleteThisTabClicked()
        }
    }

    private fun configureBottomSheet() {
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { render(it) }
            }
        }

        viewModel.commands()
            .onEach { handleCommand(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun handleCommand(command: Command) {
        when (command) {
            is Command.PlayAnimation -> {
                if (animationEnabled) {
                    playAnimation()
                } else {
                    canFinish = true
                }
            }
            is Command.ClearingComplete -> onClearAllEvent(ClearAllEvent.ClearingFinished)
            is Command.OnShow -> sendFragmentResult(FireDialog.EVENT_ON_SHOW)
            is Command.OnCancel -> {
                sendFragmentResult(FireDialog.EVENT_ON_CANCEL)
                dismiss()
            }
            is Command.OnClearStarted -> {
                if (viewModel.viewState.value.shouldRestartAfterClearing) {
                    sendFragmentResult(FireDialog.EVENT_ON_CLEAR_STARTED)
                } else {
                    sendFragmentResult(FireDialog.EVENT_CLEAR_WITHOUT_RESTART_STARTED)
                }
            }
            is Command.OnSingleTabClearComplete -> {
                sendFragmentResult(FireDialog.EVENT_ON_SINGLE_TAB_CLEAR_COMPLETE)
                onClearAllEvent(ClearAllEvent.ClearingFinished)
            }
        }
    }

    private fun sendFragmentResult(event: String) {
        parentFragmentManager.setFragmentResult(
            FireDialog.REQUEST_KEY,
            Bundle().apply { putString(FireDialog.RESULT_KEY_EVENT, event) },
        )
    }

    private fun render(state: SingleTabFireDialogViewModel.ViewState) {
        val titleRes = if (state.isDuckAiChatsSelected) {
            R.string.singleTabFireDialogTitleWithChats
        } else {
            R.string.singleTabFireDialogTitle
        }
        binding.dialogTitle.text = requireContext().getString(titleRes)

        val showDeleteThisTab = state.isSingleTabEnabled && !state.isFromTabSwitcher
        if (showDeleteThisTab) {
            binding.deleteThisTabButton.show()
        } else {
            binding.deleteThisTabButton.gone()
        }

        val subtitleParts = buildList {
            if (state.showSiteDataSubtitle) {
                add(getString(R.string.singleTabFireDialogSubtitleSiteData))
            }
            if (state.showDownloadsSubtitle) {
                add(getString(R.string.singleTabFireDialogSubtitleDownloads))
            }
            if (state.showDuckAiSubtitle) {
                add(getString(R.string.singleTabFireDialogSubtitleDuckAi))
            }
        }
        if (subtitleParts.isNotEmpty()) {
            binding.dialogSubtitle.text = subtitleParts.joinToString("\n")
            binding.dialogSubtitle.show()
        } else {
            binding.dialogSubtitle.gone()
        }
    }

    private fun removeTopPadding() {
        dialog?.findViewById<View>(MaterialR.id.design_bottom_sheet)?.apply {
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                view.updatePadding(top = 0)
                insets
            }
        }
    }

    private fun addBottomPaddingToButtons() {
        binding.fireDialogRootView.apply {
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                view.updatePadding(bottom = insets.getInsets(Type.systemBars()).bottom)
                insets
            }
        }
    }

    private fun configureFireAnimationView() {
        binding.fireAnimationView.apply {
            setAnimation(settingsDataStore.selectedFireAnimation.resId)
            setAndPropagateUpFitsSystemWindows(false)
            renderMode = RenderMode.SOFTWARE
            enableMergePathsForKitKatAndAbove(true)
        }
    }

    private fun isAnimationEnabled() = settingsDataStore.fireAnimationEnabled && isAnimatorDurationEnabled()

    private fun isAnimatorDurationEnabled(): Boolean {
        val animatorScale = Settings.Global.getFloat(requireContext().contentResolver, ANIMATOR_DURATION_SCALE, 1.0f)
        return animatorScale != 0.0f
    }

    private fun hideDialog() {
        binding.fireDialogRootView.gone()
    }

    private fun playAnimation() {
        dialog?.window?.apply {
            WindowInsetsControllerCompat(this, binding.root).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        isCancelable = false
        binding.fireAnimationView.show()
        binding.fireAnimationView.playAnimation()
        binding.fireAnimationView.addAnimatorListener(
            object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    onClearAllEvent(ClearAllEvent.AnimationFinished)
                }
            },
        )
    }

    @Synchronized
    private fun onClearAllEvent(event: ClearAllEvent) {
        if (!canFinish && _binding != null) {
            canFinish = true
            if (event is ClearAllEvent.ClearingFinished) {
                binding.fireAnimationView.addAnimatorUpdateListener(accelerateAnimatorUpdateListener)
            }
        } else {
            if (viewModel.viewState.value.shouldRestartAfterClearing) {
                clearDataAction.killAndRestartProcess(notifyDataCleared = false, enableTransitionAnimation = false)
            } else {
                dismiss()
            }
        }
    }

    private sealed class ClearAllEvent {
        data object AnimationFinished : ClearAllEvent()
        data object ClearingFinished : ClearAllEvent()
    }

    companion object {
        fun newInstance(origin: FireDialogProvider.FireDialogOrigin): SingleTabFireDialog {
            return SingleTabFireDialog().apply {
                arguments = bundleOf(ARG_ORIGIN to origin.name)
            }
        }
    }
}
