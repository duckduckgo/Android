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

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.common.utils.device.DeviceInfo
import com.google.android.material.button.MaterialButton

/**
 * Suggests Sync on the new tab page of a phone set up by a device transfer.
 *
 * It borrows the onboarding bubble so it looks like the onboarding dialogs, but it is not part of onboarding: it sends
 * no onboarding pixels, and whether it shows is decided by Sync, not by the dismissed-CTA table.
 */
data class SyncAfterDeviceTransferBubbleCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
    override val deviceInfo: DeviceInfo,
) : DaxBubbleCta(
    ctaId = CtaId.SYNC_AFTER_DEVICE_TRANSFER,
    title = R.string.syncAfterDeviceTransferDaxDialogTitle,
    description = R.string.syncAfterDeviceTransferDaxDialogDescription,
    backgroundRes = R.drawable.bg_onboarding_subscription,
    shownPixel = null,
    okPixel = null,
    closePixel = null,
    ctaPixelParam = "",
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
    deviceInfo = deviceInfo,
),
    DaxBubbleCta.ShowsWavingDax {
    override val markAsReadOnShow: Boolean = false
    override val backgroundFillSpec = BackgroundFillSpec(fillHeightDp = 190f, tabletFillHeightDp = 280f, maxHeightFraction = 0.4f)
    override val activeIncludeIds: List<Int> = listOf(R.id.primaryCta)
    override val showArrow: Boolean = true
    override val restartWavingDax: Boolean = true

    override val wavingDaxSpec = WavingDaxSpec(
        rotationDegrees = 22.62f,
        translationXDp = -114f,
        translationYDp = -288f,
        minHeightDp = 178f,
        maxHeightDp = 267f,
        anchorToCardOnTablet = false,
    )

    override fun configureContentViews(view: View) {
        view.findViewById<ImageView>(R.id.brandDesignHeaderImage)?.apply {
            setImageResource(R.drawable.sync_feature_96)
            isVisible = true
        }
        view.findViewById<TextView>(R.id.brandDesignHiddenTitle)?.gravity = Gravity.CENTER
        view.findViewById<TextView>(R.id.brandDesignTitle)?.gravity = Gravity.CENTER
        view.findViewById<TextView>(R.id.brandDesignDescription)?.gravity = Gravity.CENTER
        view.findViewById<MaterialButton>(R.id.primaryCta)?.setText(R.string.syncAfterDeviceTransferDaxDialogPrimaryCta)
    }

    override fun shouldDropAddressBarFocusWhenShown(): Boolean = true
}
