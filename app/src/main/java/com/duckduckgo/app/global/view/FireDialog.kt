/*
 * Copyright (c) 2018 DuckDuckGo
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
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import com.airbnb.lottie.RenderMode
import com.duckduckgo.app.browser.databinding.SheetFireClearDataBinding
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.view.FireDialog.FireDialogClearAllEvent.AnimationFinished
import com.duckduckgo.app.global.view.FireDialog.FireDialogClearAllEvent.ClearAllDataFinished
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.clear.getPixelValue
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_ANIMATION
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.setAndPropagateUpFitsSystemWindows
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ANIMATION_MAX_SPEED = 1.4f
private const val ANIMATION_SPEED_INCREMENT = 0.15f

@SuppressLint("NoBottomSheetDialog")
class FireDialog(
    context: Context,
    private val clearPersonalDataAction: ClearDataAction,
    private val pixel: Pixel,
    private val settingsDataStore: SettingsDataStore,
    private val userEventsStore: UserEventsStore,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val fireButtonStore: FireButtonStore,
) : BottomSheetDialog(context, com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_FireDialog) {

    private lateinit var binding: SheetFireClearDataBinding

    var clearStarted: (() -> Unit) = {}

    private val accelerateAnimatorUpdateListener = object : ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            binding.fireAnimationView.speed += ANIMATION_SPEED_INCREMENT
            if (binding.fireAnimationView.speed > ANIMATION_MAX_SPEED) {
                binding.fireAnimationView.removeUpdateListener(this)
            }
        }
    }
    private var canRestart = !animationEnabled()
    private var onClearDataOptionsDismissed: () -> Unit = {}

    init {
        val inflater = LayoutInflater.from(context)
        binding = SheetFireClearDataBinding.inflate(inflater)
        setContentView(binding.root)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.clearAllOption.setOnClickListener {
            onClearOptionClicked()
        }
        binding.cancelOption.setOnClickListener {
            cancel()
        }

        if (animationEnabled()) {
            configureFireAnimationView()
        }
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun configureFireAnimationView() {
        binding.fireAnimationView.setAnimation(settingsDataStore.selectedFireAnimation.resId)
        /**
         * BottomSheetDialog wraps provided Layout into a CoordinatorLayout.
         * We need to set FitsSystemWindows false programmatically to all parents in order to render layout and animation full screen
         */
        binding.fireAnimationView.setAndPropagateUpFitsSystemWindows(false)
        binding.fireAnimationView.setRenderMode(RenderMode.SOFTWARE)
        binding.fireAnimationView.enableMergePathsForKitKatAndAbove(true)
    }

    private fun onClearOptionClicked() {
        pixel.enqueueFire(FIRE_DIALOG_CLEAR_PRESSED)
        pixel.enqueueFire(
            pixel = FIRE_DIALOG_ANIMATION,
            parameters = mapOf(FIRE_ANIMATION to settingsDataStore.selectedFireAnimation.getPixelValue()),
        )
        hideClearDataOptions()
        if (animationEnabled()) {
            playAnimation()
        }
        clearStarted()

        appCoroutineScope.launch(dispatcherProvider.io()) {
            fireButtonStore.incrementFireButtonUseCount()
            userEventsStore.registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)
            clearPersonalDataAction.clearTabsAndAllDataAsync(appInForeground = true, shouldFireDataClearPixel = true)
            clearPersonalDataAction.setAppUsedSinceLastClearFlag(false)
            onFireDialogClearAllEvent(ClearAllDataFinished)
        }
    }

    private fun animationEnabled() = settingsDataStore.fireAnimationEnabled && animatorDurationEnabled()

    private fun animatorDurationEnabled(): Boolean {
        val animatorScale = Settings.Global.getFloat(context.contentResolver, ANIMATOR_DURATION_SCALE, 1.0f)
        return animatorScale != 0.0f
    }

    private fun playAnimation() {
        window?.navigationBarColor = ContextCompat.getColor(context, CommonR.color.black)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        binding.fireAnimationView.show()
        binding.fireAnimationView.playAnimation()
        binding.fireAnimationView.addAnimatorListener(
            object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    onFireDialogClearAllEvent(AnimationFinished)
                }
            },
        )
    }

    private fun hideClearDataOptions() {
        binding.fireDialogRootView.gone()
        onClearDataOptionsDismissed()
        /*
         * Avoid calling callback twice when view is detached.
         * We handle this callback here to ensure pixel is sent before process restarts
         */
        onClearDataOptionsDismissed = {}
    }

    @Synchronized
    private fun onFireDialogClearAllEvent(event: FireDialogClearAllEvent) {
        if (!canRestart) {
            canRestart = true
            if (event is ClearAllDataFinished) {
                binding.fireAnimationView.addAnimatorUpdateListener(accelerateAnimatorUpdateListener)
            }
        } else {
            clearPersonalDataAction.killAndRestartProcess(notifyDataCleared = false)
        }
    }

    private sealed class FireDialogClearAllEvent {
        data object AnimationFinished : FireDialogClearAllEvent()
        data object ClearAllDataFinished : FireDialogClearAllEvent()
    }
}
