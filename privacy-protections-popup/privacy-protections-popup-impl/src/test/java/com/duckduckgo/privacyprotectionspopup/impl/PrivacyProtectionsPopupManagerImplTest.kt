/*
 * Copyright (c) 2023 DuckDuckGo
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISABLE_PROTECTIONS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISSED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISS_CLICKED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PrivacyProtectionsPopupManagerImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val featureAvailability = FakePrivacyProtectionsPopupFeatureAvailability()

    private val protectionsStateProvider = FakeProtectionsStateProvider()

    private val subject = PrivacyProtectionsPopupManagerImpl(
        appCoroutineScope = coroutineRule.testScope,
        featureAvailability = featureAvailability,
        protectionsStateProvider = protectionsStateProvider,
    )

    @Test
    fun whenRefreshIsTriggeredThenEmitsUpdateToShowPopup() = runTest {
        subject.viewState.test {
            assertFalse(awaitItem().visible)
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList())
            expectNoEvents()
            subject.onPageRefreshTriggeredByUser()
            assertTrue(awaitItem().visible)
            expectNoEvents()
        }
    }

    @Test
    fun whenRefreshIsTriggeredThenPopupIsShown() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList())
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)
    }

    @Test
    fun whenFeatureIsDisabledThenPopupIsNotShown() = runTest {
        featureAvailability.featureAvailable = false
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList())
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenProtectionsAreDisabledThenPopupIsNotShown() = runTest {
        protectionsStateProvider.protectionsEnabled = false
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList())
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenUrlIsMissingThenPopupIsNotShown() = runTest {
        subject.onPageLoaded(url = "", httpErrorCodes = emptyList())
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenPageLoadedWithHttpErrorThenPopupIsNotShown() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = listOf(500))
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenPageIsChangedThenPopupIsDismissed() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList())
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)

        subject.onPageLoaded(url = "https://www.example2.com", httpErrorCodes = emptyList())

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenDismissEventIsHandledThenViewStateIsUpdated() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList())
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)

        subject.onUiEvent(DISMISSED)

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenDismissButtonClickedEventIsHandledThenPopupIsDismissed() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList())
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)

        subject.onUiEvent(DISMISS_CLICKED)

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenDisableProtectionsClickedEventIsHandledThenPopupIsDismissed() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList())
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)

        subject.onUiEvent(DISABLE_PROTECTIONS_CLICKED)

        assertPopupVisible(visible = false)
    }

    private suspend fun assertPopupVisible(visible: Boolean) {
        subject.viewState.test {
            assertEquals(visible, awaitItem().visible)
            expectNoEvents()
        }
    }
}

private class FakePrivacyProtectionsPopupFeatureAvailability : PrivacyProtectionsPopupFeatureAvailability {

    var featureAvailable = true

    override suspend fun isAvailable(): Boolean = featureAvailable
}

private class FakeProtectionsStateProvider : ProtectionsStateProvider {

    private var _protectionsEnabled = MutableStateFlow(true)

    var protectionsEnabled: Boolean
        set(value) {
            _protectionsEnabled.value = value
        }
        get() = _protectionsEnabled.value

    override fun areProtectionsEnabled(domain: String): Flow<Boolean> = _protectionsEnabled.asStateFlow()
}
