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

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.view.html
import kotlinx.android.synthetic.main.include_dax_dialog.*
import kotlinx.android.synthetic.main.content_onboarding_welcome.*
import kotlinx.android.synthetic.main.content_onboarding_welcome.longDescriptionContainer


class WelcomePage : OnboardingPageFragment() {

    override fun layoutResource(): Int = R.layout.content_onboarding_welcome

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        primaryCta.setOnClickListener { onContinuePressed() }
        var daxText = ""

        context?.let {
            daxText = it.getString(R.string.onboardingDaxText)
            hiddenText.text = daxText.html(it)
            dialogText.setTextColor(ContextCompat.getColor(it, R.color.grayishBrown))
            cardView.backgroundTintList = ContextCompat.getColorStateList(it, R.color.white)
        }
        triangle.setImageResource(R.drawable.ic_triangle_bubble_white)

        ViewCompat.animate(welcomeContent as View)
            .alpha(0f)
            .setDuration(400)
            .setStartDelay(1000)
            .withEndAction {
                ViewCompat.animate(daxCtaContainer)
                    .alpha(1f)
                    .setDuration(400)
                    .withEndAction {
                        dialogText.startTypingAnimation(daxText)
                    }
            }
    }

    override fun onResume() {
        super.onResume()

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
}