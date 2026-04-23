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

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.button.DaxButtonPrimary
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.extensions.html

data class DaxMainNetworkBrandDesignUpdateContextualCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    val network: String,
    val siteHost: String,
    override val isLightTheme: Boolean,
) : OnboardingDaxDialogCta.BrandDesignContextualDaxDialogCta(
    ctaId = CtaId.DAX_DIALOG_NETWORK,
    description = null,
    buttonText = R.string.daxDialogGotIt,
    shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
    okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
    cancelPixel = null,
    closePixel = AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
    ctaPixelParam = Pixel.PixelValues.DAX_NETWORK_CTA_1,
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
) {
    override val activeIncludeId: Int = R.id.contextualBrandDesignPrimaryCtaContent

    override fun configureContentViews(view: View) {
        val context = view.context
        view.findViewById<DaxTextView>(R.id.contextualBrandDesignDescription)?.apply {
            text = getTrackersDescription(context).html(context)
        }
        view.findViewById<DaxButtonPrimary>(R.id.contextualBrandDesignPrimaryCta)?.setText(R.string.daxDialogGotIt)
    }

    override fun setOnPrimaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<View>(R.id.contextualBrandDesignPrimaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    @VisibleForTesting
    fun getTrackersDescription(context: Context): String =
        if (isFromSameNetworkDomain()) {
            context.resources.getString(
                R.string.daxMainNetworkCtaText,
                network,
                Uri.parse(siteHost).baseHost?.removePrefix("m."),
                network,
            )
        } else {
            context.resources.getString(
                R.string.daxMainNetworkOwnedCtaText,
                network,
                Uri.parse(siteHost).baseHost?.removePrefix("m."),
                network,
            )
        }

    private fun isFromSameNetworkDomain(): Boolean = MAIN_TRACKER_DOMAINS.any { siteHost.contains(it) }

    private companion object {
        private val MAIN_TRACKER_DOMAINS = listOf("facebook", "google")
    }
}
