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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.include_dax_dialog_cta.*
import kotlinx.android.synthetic.main.sheet_fire_clear_data.*
import kotlinx.coroutines.*
import timber.log.Timber

class FireDialog(
    context: Context,
    private val ctaViewModel: CtaViewModel,
    private val clearPersonalDataAction: ClearPersonalDataAction
) : BottomSheetDialog(context, R.style.FireDialog), CoroutineScope by MainScope() {

    var clearStarted: (() -> Unit) = {}
    var clearComplete: (() -> Unit) = {}

    private var canRestart = false
    private var onClearDataOptionsDismissed: () -> Unit = {}

    init {
        setContentView(R.layout.sheet_fire_clear_data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        launch {
            ctaViewModel.getFireDialogCta()?.let { cta ->
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
        }

        clearAllOption.setOnClickListener {
            Timber.i("FireAnimation clearAllStarted")
            hideClearDataOptions()
            playAnimation()
            clearStarted()

            GlobalScope.launch {
                clearPersonalDataAction.clearTabsAndAllDataAsync(appInForeground = true, shouldFireDataClearPixel = true)
                clearPersonalDataAction.setAppUsedSinceLastClearFlag(false)
                killAndRestartIfNecessary()
                Timber.i("FireAnimation clearAllFinished")
            }
        }

        cancelOption.setOnClickListener {
            cancel()
        }

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
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
                Timber.i("FireAnimation onAnimationEnd")
                killAndRestartIfNecessary()
            }
        })
    }

    private fun killAndRestartIfNecessary() {
        synchronized(this) {
            if (!canRestart) {
                canRestart = true
            } else {
                Timber.i("FireAnimation killAndRestartProcess")
                dismiss()
                clearPersonalDataAction.killAndRestartProcess()
            }
        }
    }
}
