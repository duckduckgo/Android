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
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
import androidx.core.content.ContextCompat
import androidx.core.view.doOnDetach
import androidx.core.view.isVisible
import com.airbnb.lottie.RenderMode
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.cta.ui.DaxFireDialogCta
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.view.FireDialog.FireDialogClearAllEvent.AnimationFinished
import com.duckduckgo.app.global.view.FireDialog.FireDialogClearAllEvent.ClearAllDataFinished
import com.duckduckgo.app.settings.clear.getPixelValue
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_ANIMATION
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.include_dax_dialog_cta.*
import kotlinx.android.synthetic.main.sheet_fire_clear_data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

private const val ANIMATION_MAX_SPEED = 1.4f
private const val ANIMATION_SPEED_INCREMENT = 0.15f

class FireDialog(
    context: Context,
    private val ctaViewModel: CtaViewModel,
    private val clearPersonalDataAction: ClearDataAction,
    private val pixel: Pixel,
    private val settingsDataStore: SettingsDataStore,
    private val userEventsStore: UserEventsStore,
    private val appCoroutineScope: CoroutineScope
) : BottomSheetDialog(context, R.style.FireDialog), CoroutineScope by MainScope() {

    var clearStarted: (() -> Unit) = {}
    val ctaVisible: Boolean
        get() = daxCtaContainer?.isVisible == true

    private val accelerateAnimatorUpdateListener = object : ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator?) {
            fireAnimationView.speed += ANIMATION_SPEED_INCREMENT
            if (fireAnimationView.speed > ANIMATION_MAX_SPEED) {
                fireAnimationView.removeUpdateListener(this)
            }
        }
    }
    private var canRestart = !animationEnabled()
    private var onClearDataOptionsDismissed: () -> Unit = {}

    init {
        setContentView(R.layout.sheet_fire_clear_data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        launch {
            ctaViewModel.getFireDialogCta()?.let {
                configureFireDialogCta(it)
            }
        }
        clearAllOption.setOnClickListener {
            onClearOptionClicked()
        }
        cancelOption.setOnClickListener {
            cancel()
        }

        if (animationEnabled()) {
            configureFireAnimationView()
        }
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun configureFireAnimationView() {
        fireAnimationView.setAnimation(settingsDataStore.selectedFireAnimation.resId)
        /**
         * BottomSheetDialog wraps provided Layout into a CoordinatorLayout.
         * We need to set FitsSystemWindows false programmatically to all parents in order to render layout and animation full screen
         */
        fireAnimationView.setAndPropagateUpFitsSystemWindows(false)
        fireAnimationView.setRenderMode(RenderMode.SOFTWARE)
        fireAnimationView.enableMergePathsForKitKatAndAbove(true)
    }

    private fun configureFireDialogCta(cta: DaxFireDialogCta) {
        fireCtaViewStub.inflate()
        cta.showCta(daxCtaContainer)
        ctaViewModel.onCtaShown(cta)
        onClearDataOptionsDismissed = {
            appCoroutineScope.launch {
                ctaViewModel.onUserDismissedCta(cta)
            }
        }
        daxCtaContainer.doOnDetach {
            onClearDataOptionsDismissed()
        }
    }

    private fun onClearOptionClicked() {
        pixel.enqueueFire(if (ctaVisible) FIRE_DIALOG_PROMOTED_CLEAR_PRESSED else FIRE_DIALOG_CLEAR_PRESSED)
        pixel.enqueueFire(pixel = FIRE_DIALOG_ANIMATION, parameters = mapOf(FIRE_ANIMATION to settingsDataStore.selectedFireAnimation.getPixelValue()))
        hideClearDataOptions()
        if (animationEnabled()) {
            playAnimation()
        }
        clearStarted()

        appCoroutineScope.launch {
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
        window?.navigationBarColor = ContextCompat.getColor(context, R.color.black)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        fireAnimationView.show()
        fireAnimationView.playAnimation()
        fireAnimationView.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {}
            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationStart(animation: Animator?) {}
            override fun onAnimationEnd(animation: Animator?) {
                onFireDialogClearAllEvent(AnimationFinished)
            }
        })
    }

    private fun hideClearDataOptions() {
        fireDialogRootView.gone()
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
                fireAnimationView.addAnimatorUpdateListener(accelerateAnimatorUpdateListener)
            }
        } else {
            clearPersonalDataAction.killAndRestartProcess(notifyDataCleared = false)
        }
    }

    private sealed class FireDialogClearAllEvent {
        object AnimationFinished : FireDialogClearAllEvent()
        object ClearAllDataFinished : FireDialogClearAllEvent()
    }
}
