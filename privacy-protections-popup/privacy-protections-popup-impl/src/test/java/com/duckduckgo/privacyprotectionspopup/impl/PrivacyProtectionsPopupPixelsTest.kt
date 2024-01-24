/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.DAILY
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.UNIQUE
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupExperimentVariant.TEST
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.Params.PARAM_POPUP_TRIGGER_COUNT
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class PrivacyProtectionsPopupPixelsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pixel: Pixel = mock()

    private val dataStore = FakePrivacyProtectionsPopupDataStore()

    private val subject = PrivacyProtectionsPopupPixelsImpl(
        pixelSender = pixel,
        paramsProvider = PrivacyProtectionsPopupExperimentPixelParamsProviderImpl(dataStore),
        appCoroutineScope = coroutineRule.testScope,
        dataStore = dataStore,
    )

    @Before
    fun setUp() {
        runBlocking { dataStore.setExperimentVariant(TEST) }
    }

    @Test
    fun whenExperimentVariantIsAssignedThenPixelIsSent() = runTest {
        subject.reportExperimentVariantAssigned()

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.EXPERIMENT_VARIANT_ASSIGNED,
            parameters = DEFAULT_PARAMS,
            type = UNIQUE,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenPopupIsTriggeredThenPixelIsSent() = runTest {
        subject.reportPopupTriggered()

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.POPUP_TRIGGERED,
            parameters = DEFAULT_PARAMS,
            type = COUNT,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenProtectionsAreDisabledThenPixelsAreSent() = runTest {
        subject.reportProtectionsDisabled()

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.PROTECTIONS_DISABLED,
            parameters = DEFAULT_PARAMS,
            type = COUNT,
        )

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.PROTECTIONS_DISABLED_UNIQUE,
            parameters = DEFAULT_PARAMS,
            type = UNIQUE,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenPrivacyDashboardIsOpenedThenPixelsAreSent() = runTest {
        subject.reportPrivacyDashboardOpened()

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.PRIVACY_DASHBOARD_OPENED,
            parameters = DEFAULT_PARAMS,
            type = COUNT,
        )

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.PRIVACY_DASHBOARD_OPENED_UNIQUE,
            parameters = DEFAULT_PARAMS,
            type = UNIQUE,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDismissButtonsIsClickedThenPixelIsSent() = runTest {
        subject.reportPopupDismissedViaButton()

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.POPUP_DISMISSED_VIA_BUTTON,
            parameters = DEFAULT_PARAMS,
            type = COUNT,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenDoNotShowAgainIsClickedThenPixelIsSentWithTriggerCountParam() = runTest {
        val popupTriggerCount = 4
        dataStore.setPopupTriggerCount(popupTriggerCount)
        subject.reportDoNotShowAgainClicked()
        val extraParams = mapOf(PARAM_POPUP_TRIGGER_COUNT to popupTriggerCount.toString())

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.DO_NOT_SHOW_AGAIN_CLICKED,
            parameters = DEFAULT_PARAMS + extraParams,
            type = UNIQUE,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenPopupIsDismissedViaClickOutsideThenPixelIsSent() = runTest {
        subject.reportPopupDismissedViaClickOutside()

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.POPUP_DISMISSED_VIA_CLICK_OUTSIDE,
            parameters = DEFAULT_PARAMS,
            type = COUNT,
        )

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenPageIsRefreshedThenPixelIsSent() = runTest {
        subject.reportPageRefreshOnPossibleBreakage()

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.PAGE_REFRESH_ON_POSSIBLE_BREAKAGE,
            parameters = DEFAULT_PARAMS,
            type = COUNT,
        )

        verify(pixel).fire(
            pixel = PrivacyProtectionsPopupPixelName.PAGE_REFRESH_ON_POSSIBLE_BREAKAGE_DAILY,
            parameters = DEFAULT_PARAMS,
            type = DAILY,
        )

        verifyNoMoreInteractions(pixel)
    }

    private companion object {
        val DEFAULT_PARAMS = mapOf("privacy_protections_popup_experiment_variant" to "test")
    }
}
