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

import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.common.utils.device.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BrandDesignBackgroundFillSpecTest {

    private val onboardingStore: OnboardingStore = mock()
    private val appInstallStore: AppInstallStore = mock()
    private val deviceInfo: DeviceInfo = mock()

    @Before
    fun setup() {
        whenever(onboardingStore.isCustomAiOnboardingFlow()).thenReturn(false)
        whenever(onboardingStore.getSearchOptions()).thenReturn(emptyList())
    }

    @Test
    fun endBubbleHasFillSpec() {
        val cta =
            DaxEndBrandDesignUpdateBubbleCta(onboardingStore, appInstallStore, isLightTheme = true, deviceInfo, onboardingImprovementsEnabled = true)
        assertEquals(280f, cta.backgroundFillSpec?.fillHeightDp)
    }

    @Test
    fun subscriptionBubbleHasFillSpec() {
        val cta =
            DaxSubscriptionBrandDesignUpdateBubbleCta(
                onboardingStore,
                appInstallStore,
                isLightTheme = true,
                deviceInfo,
                isFreeTrialCopy = false,
                onboardingImprovementsEnabled = true,
            )
        assertEquals(190f, cta.backgroundFillSpec?.fillHeightDp)
    }

    @Test
    fun contextualEndHasFillSpec() {
        val cta = DaxEndBrandDesignUpdateContextualCta(onboardingStore, appInstallStore, isLightTheme = true, deviceInfo)
        assertEquals(160f, cta.backgroundFillSpec?.fillHeightDp)
    }

    @Test
    fun tryASearchBubbleHasNoFillSpec() {
        val cta = DaxTryASearchBrandDesignUpdateBubbleCta(onboardingStore, appInstallStore, isLightTheme = true, deviceInfo)
        assertNull(cta.backgroundFillSpec)
    }
}
