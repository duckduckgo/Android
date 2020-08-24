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
import android.content.Context
import android.os.Bundle
import androidx.core.view.doOnDetach
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.cta.ui.DaxFireCta
import com.duckduckgo.app.global.view.FireDialog.FireDialogClearAllEvent.AnimationFinished
import com.duckduckgo.app.global.view.FireDialog.FireDialogClearAllEvent.ClearDataFinished
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.FireButtonEducation
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.include_dax_dialog_cta.*
import kotlinx.android.synthetic.main.sheet_fire_clear_data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber

class FireDialog(
    context: Context,
    private val ctaViewModel: CtaViewModel,
    private val clearPersonalDataAction: ClearPersonalDataAction,
    private val variantManager: VariantManager
) : BottomSheetDialog(context, R.style.FireDialog), CoroutineScope by MainScope() {

    var clearStarted: (() -> Unit) = {}
    var clearComplete: (() -> Unit) = {}

    private var speedUpAnimation: Boolean = false
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

        fireAnimationView.setAndPropagateUpFitsSystemWindows(false)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun configureFireDialogCta(cta: DaxFireCta) {
        fireCtaViewStub.inflate()
        cta.showCta(daxCtaContainer)
        ctaViewModel.onCtaShown(cta)
        onClearDataOptionsDismissed = {
            GlobalScope.launch {
                Timber.i("FireAnimation userDismissedFireCta")
                ctaViewModel.onUserDismissedCta(cta)
            }
        }
        daxCtaContainer.doOnDetach {
            onClearDataOptionsDismissed()
        }
    }

    private fun onClearOptionClicked() {
        hideClearDataOptions()
        if (animationEnabled()) {
            playAnimation()
        }
        clearStarted()

        GlobalScope.launch {
            clearPersonalDataAction.clearTabsAndAllDataAsync(appInForeground = true, shouldFireDataClearPixel = true)
            clearPersonalDataAction.setAppUsedSinceLastClearFlag(false)
            killAndRestartIfAllTasksCompleted(ClearDataFinished)
        }
    }

    private fun animationEnabled() = variantManager.getVariant().hasFeature(FireButtonEducation)

    private fun playAnimation() {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        fireAnimationView.show()
        fireAnimationView.playAnimation()
        fireAnimationView.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {}
            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationStart(animation: Animator?) {}
            override fun onAnimationEnd(animation: Animator?) {
                killAndRestartIfAllTasksCompleted(AnimationFinished)
            }
        })
        fireAnimationView.addAnimatorUpdateListener {
            if (speedUpAnimation) {
                fireAnimationView.speed += 0.15f
                if (fireAnimationView.speed > 1.4f) {
                    speedUpAnimation = false
                }
            }
        }
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
    private fun killAndRestartIfAllTasksCompleted(event: FireDialogClearAllEvent) {
        if (!canRestart) {
            speedUpAnimation = event is ClearDataFinished
            canRestart = true
        } else {
            clearPersonalDataAction.killAndRestartProcess()
        }
    }

    private sealed class FireDialogClearAllEvent {
        object AnimationFinished : FireDialogClearAllEvent()
        object ClearDataFinished : FireDialogClearAllEvent()
    }
}
