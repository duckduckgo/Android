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

package com.duckduckgo.app.cta.ui

import android.text.Html
import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.appendIconToText
import com.duckduckgo.mobile.android.R as CommonR

data class DaxDuckAiEndBubbleCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : DaxBubbleCta(
    ctaId = CtaId.DAX_DUCK_AI_END,
    title = R.string.onboardingDuckAiEndCtaTitle,
    description = R.string.onboardingDuckAiEndCtaDescription,
    primaryCta = R.string.onboardingDuckAiEndCtaButton,
    shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
    okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
    ctaPixelParam = "duck_ai_end_cta",
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
) {
    override val markAsReadOnShow: Boolean = true

    override fun showCta(
        view: View,
        onTypingAnimationFinished: () -> Unit,
    ) {
        // Animator types plain String only, so apply the icon-suffixed description after it finishes.
        val wrappedCallback = {
            val context = view.context
            val descriptionHtml = Html.fromHtml(context.getString(R.string.onboardingDuckAiEndCtaDescription), Html.FROM_HTML_MODE_COMPACT)
            view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta)?.text =
                context.appendIconToText(descriptionHtml, CommonR.drawable.ic_ai_chat_16)
            onTypingAnimationFinished()
        }
        super.showCta(view, wrappedCallback)
    }
}
