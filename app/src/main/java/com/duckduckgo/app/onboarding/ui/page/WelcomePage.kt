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
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DefaultRoleBrowserDialogExperiment
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.android.synthetic.main.content_onboarding_welcome.*
import kotlinx.android.synthetic.main.include_dax_dialog_cta.*
import javax.inject.Inject

class WelcomePage : OnboardingPageFragment() {

    @Inject
    lateinit var defaultRoleBrowserDialogExperiment: DefaultRoleBrowserDialogExperiment

    @Inject
    lateinit var pixel: Pixel

    private var ctaText: String = ""
    private var welcomeAnimation: ViewPropertyAnimatorCompat? = null
    private var typingAnimation: ViewPropertyAnimatorCompat? = null

    override fun layoutResource(): Int = R.layout.content_onboarding_welcome

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        primaryCta.setOnClickListener { launchDefaultBrowserDialogOrContinue() }

        configureDaxCta()
        beginWelcomeAnimation(ctaText)
    }

    private fun launchDefaultBrowserDialogOrContinue() {
        if (defaultRoleBrowserDialogExperiment.shouldShowExperiment()) {
            val intent = defaultRoleBrowserDialogExperiment.createIntent(requireContext())
            if (intent != null) {
                startActivityForResult(intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
            } else {
                onContinuePressed()
            }
        } else {
            onContinuePressed()
        }
    }

    override fun onResume() {
        super.onResume()
        applyFullScreenFlags()
    }

    override fun onDestroy() {
        super.onDestroy()
        welcomeAnimation?.cancel()
        typingAnimation?.cancel()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == DEFAULT_BROWSER_ROLE_MANAGER_DIALOG) {
            defaultRoleBrowserDialogExperiment.experimentShown()

            val pixelParam = mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString())
            val pixelName = if (resultCode == RESULT_OK) {
                Pixel.PixelName.DEFAULT_BROWSER_SET
            } else {
                Pixel.PixelName.DEFAULT_BROWSER_NOT_SET
            }
            pixel.fire(pixelName, pixelParam)
            onContinuePressed()
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
            dialogTextCta.setTextColor(ContextCompat.getColor(it, R.color.grayishBrown))
            cardView.backgroundTintList = ContextCompat.getColorStateList(it, R.color.white)
        }
        triangle.setImageResource(R.drawable.ic_triangle_bubble_white)
    }

    private fun beginWelcomeAnimation(ctaText: String) {
        welcomeAnimation = ViewCompat.animate(welcomeContent as View)
            .alpha(MIN_ALPHA)
            .setDuration(ANIMATION_DURATION)
            .setStartDelay(ANIMATION_DELAY)
            .withEndAction {
                typingAnimation = ViewCompat.animate(daxCtaContainer)
                    .alpha(MAX_ALPHA)
                    .setDuration(ANIMATION_DURATION)
                    .withEndAction {
                        dialogTextCta.startTypingAnimation(ctaText)
                    }
            }
    }

    companion object {
        private const val MIN_ALPHA = 0f
        private const val MAX_ALPHA = 1f
        private const val ANIMATION_DURATION = 400L
        private const val ANIMATION_DELAY = 1400L

        private const val DEFAULT_BROWSER_ROLE_MANAGER_DIALOG = 101
    }
}
