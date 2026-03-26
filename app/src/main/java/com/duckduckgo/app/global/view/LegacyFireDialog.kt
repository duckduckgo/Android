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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.airbnb.lottie.RenderMode
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.SheetFireClearDataBinding
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_ANIMATION
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_CLEAR_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.getPixelValue
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_ANIMATION
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.setAndPropagateUpFitsSystemWindows
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.DateProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.R as MaterialR

private const val ANIMATION_MAX_SPEED = 1.4f
private const val ANIMATION_SPEED_INCREMENT = 0.15f

@InjectWith(FragmentScope::class)
class LegacyFireDialog : BottomSheetDialogFragment(), FireDialog {
    @AppCoroutineScope
    @Inject
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var clearDataAction: ClearDataAction

    @Inject
    lateinit var dataClearingWideEvent: DataClearingWideEvent

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var userEventsStore: UserEventsStore

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var fireButtonStore: FireButtonStore

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var dateProvider: DateProvider

    private var _binding: SheetFireClearDataBinding? = null
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

    private var canRestart = false

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
        _binding = SheetFireClearDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            pixel.enqueueFire(AppPixelName.FIRE_DIALOG_SHOWN)
        }

        canRestart = !animationEnabled()

        setupLayout()
        configureBottomSheet()

        if (appBuildConfig.sdkInt == 26) {
            dialog?.window?.navigationBarColor = ContextCompat.getColor(requireContext(), CommonR.color.translucentDark)
        } else if (appBuildConfig.sdkInt in 27..<30) {
            dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }

        removeTopPadding()
        addBottomPaddingToButtons()

        if (animationEnabled()) {
            configureFireAnimationView()
        }
    }

    override fun onStart() {
        super.onStart()
        parentFragmentManager.setFragmentResult(
            FireDialog.REQUEST_KEY,
            Bundle().apply {
                putString(FireDialog.RESULT_KEY_EVENT, FireDialog.EVENT_ON_SHOW)
            },
        )
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        parentFragmentManager.setFragmentResult(
            FireDialog.REQUEST_KEY,
            Bundle().apply {
                putString(FireDialog.RESULT_KEY_EVENT, FireDialog.EVENT_ON_CANCEL)
            },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupLayout() {
        binding.apply {
            clearAllOption.setOnClickListener {
                onClearOptionClicked()
            }
            cancelOption.setOnClickListener {
                parentFragmentManager.setFragmentResult(
                    FireDialog.REQUEST_KEY,
                    Bundle().apply {
                        putString(FireDialog.RESULT_KEY_EVENT, FireDialog.EVENT_ON_CANCEL)
                    },
                )
                dismiss()
            }

            if (settingsDataStore.clearDuckAiData) {
                clearAllOption.setPrimaryText(requireContext().getString(com.duckduckgo.app.browser.R.string.fireClearAllPlusDuckChats))
            }
        }
    }

    private fun configureBottomSheet() {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
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

    private fun onClearOptionClicked() {
        trySendDailyClearOptionClicked()
        pixel.enqueueFire(FIRE_DIALOG_CLEAR_PRESSED)
        pixel.enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING)
        pixel.enqueueFire(
            pixel = FIRE_DIALOG_ANIMATION,
            parameters = mapOf(FIRE_ANIMATION to settingsDataStore.selectedFireAnimation.getPixelValue()),
        )
        hideClearDataOptions()
        if (animationEnabled()) {
            playAnimation()
        } else {
            // Animation was enabled when dialog opened but is now disabled.
            // Update canRestart so ClearAllDataFinished can complete the flow.
            canRestart = true
        }
        parentFragmentManager.setFragmentResult(
            FireDialog.REQUEST_KEY,
            Bundle().apply {
                putString(FireDialog.RESULT_KEY_EVENT, FireDialog.EVENT_ON_CLEAR_STARTED)
            },
        )

        appCoroutineScope.launch(dispatcherProvider.io()) {
            fireButtonStore.incrementFireButtonUseCount()
            userEventsStore.registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)
            dataClearingWideEvent.startLegacy(
                entryPoint = DataClearingWideEvent.EntryPoint.LEGACY_FIRE_DIALOG,
                clearWhatOption = ClearWhatOption.CLEAR_TABS_AND_DATA,
                clearDuckAiData = settingsDataStore.clearDuckAiData,
            )
            try {
                clearDataAction.clearTabsAndAllDataAsync(appInForeground = true, shouldFireDataClearPixel = true)
                clearDataAction.setAppUsedSinceLastClearFlag(false)
                dataClearingWideEvent.finishSuccess()
            } catch (e: Exception) {
                dataClearingWideEvent.finishFailure(e)
                throw e
            }
            onFireDialogClearAllEvent(FireDialogClearAllEvent.ClearAllDataFinished)
        }
    }

    private fun animationEnabled() = settingsDataStore.fireAnimationEnabled && animatorDurationEnabled()

    private fun animatorDurationEnabled(): Boolean {
        val animatorScale = Settings.Global.getFloat(requireContext().contentResolver, ANIMATOR_DURATION_SCALE, 1.0f)
        return animatorScale != 0.0f
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
                    onFireDialogClearAllEvent(FireDialogClearAllEvent.AnimationFinished)
                }
            },
        )
    }

    private fun hideClearDataOptions() {
        binding.fireDialogRootView.gone()
    }

    @Synchronized
    private fun onFireDialogClearAllEvent(event: FireDialogClearAllEvent) {
        if (!canRestart && _binding != null) {
            canRestart = true
            if (event is FireDialogClearAllEvent.ClearAllDataFinished) {
                binding.fireAnimationView.addAnimatorUpdateListener(accelerateAnimatorUpdateListener)
            }
        } else {
            clearDataAction.killAndRestartProcess(notifyDataCleared = false, enableTransitionAnimation = false)
        }
    }

    private fun trySendDailyClearOptionClicked() {
        val now = dateProvider.getUtcIsoLocalDate()
        val timestamp = fireButtonStore.lastEventSendTime

        if (timestamp == null || now > timestamp) {
            fireButtonStore.storeLastFireButtonClearEventTime(now)
            pixel.enqueueFire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY)
        }
    }

    private sealed class FireDialogClearAllEvent {
        data object AnimationFinished : FireDialogClearAllEvent()
        data object ClearAllDataFinished : FireDialogClearAllEvent()
    }

    companion object {
        fun newInstance(): LegacyFireDialog {
            return LegacyFireDialog()
        }
    }
}
