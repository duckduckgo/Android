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

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISABLE_PROTECTIONS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISSED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DONT_SHOW_AGAIN_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.PRIVACY_DASHBOARD_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupExperimentVariant.CONTROL
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupExperimentVariant.TEST
import com.duckduckgo.privacyprotectionspopup.impl.db.PopupDismissDomainRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PrivacyProtectionsPopupManagerImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val featureFlag = FakeFeatureToggleFactory.create(PrivacyProtectionsPopupFeature::class.java)

    private val protectionsStateProvider = FakeProtectionsStateProvider()

    private val timeProvider = FakeTimeProvider()

    private val popupDismissDomainRepository = FakePopupDismissDomainRepository()

    private val userAllowListRepository = FakeUserAllowlistRepository()

    private val dataStore = FakePrivacyProtectionsPopupDataStore()

    private val duckDuckGoUrlDetector = FakeDuckDuckGoUrlDetector()

    private val variantRandomizer = FakePrivacyProtectionsPopupExperimentVariantRandomizer()

    private val pixels: PrivacyProtectionsPopupPixels = mock()

    private val subject = PrivacyProtectionsPopupManagerImpl(
        appCoroutineScope = coroutineRule.testScope,
        featureFlag = featureFlag,
        dataProvider = PrivacyProtectionsPopupManagerDataProviderImpl(
            protectionsStateProvider = protectionsStateProvider,
            popupDismissDomainRepository = popupDismissDomainRepository,
            dataStore = dataStore,
        ),
        timeProvider = timeProvider,
        popupDismissDomainRepository = popupDismissDomainRepository,
        userAllowListRepository = userAllowListRepository,
        dataStore = dataStore,
        duckDuckGoUrlDetector = duckDuckGoUrlDetector,
        variantRandomizer = variantRandomizer,
        pixels = pixels,
    )

    @Before
    fun setup() {
        featureFlag.self().setRawStoredState(State(enable = true))
    }

    @Test
    fun whenRefreshIsTriggeredThenEmitsUpdateToShowPopup() = runTest {
        val toggleUsedAt = timeProvider.time - Duration.ofDays(32)
        dataStore.setToggleUsageTimestamp(toggleUsedAt)

        subject.viewState.test {
            assertEquals(PrivacyProtectionsPopupViewState.Gone, awaitItem())
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            expectNoEvents()
            subject.onPageRefreshTriggeredByUser(true)
            assertTrue(awaitItem() is PrivacyProtectionsPopupViewState.Visible)
            expectNoEvents()
        }
    }

    @Test
    fun whenRefreshIsTriggeredThenPopupIsShown() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)
        }
    }

    @Test
    fun whenUrlIsDuckDuckGoThenPopupIsNotShown() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://duckduckgo.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenFeatureIsDisabledThenPopupIsNotShown() = runTest {
        featureFlag.self().setRawStoredState(State(enable = false))
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenProtectionsAreDisabledThenPopupIsNotShown() = runTest {
        protectionsStateProvider.protectionsEnabled = false

        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenUrlIsMissingThenPopupIsNotShown() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenPageLoadedWithHttpErrorThenPopupIsNotShown() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = listOf(500), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenPageLoadedWithBrowserErrorThenPopupIsNotShown() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = true)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenPageIsChangedThenPopupIsNotDismissed() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)

            subject.onPageLoaded(url = "https://www.example2.com", httpErrorCodes = emptyList(), hasBrowserError = false)

            expectNoEvents()
        }
    }

    @Test
    fun whenDismissEventIsHandledThenViewStateIsUpdated() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)

            subject.onUiEvent(DISMISSED)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenDismissButtonClickedEventIsHandledThenPopupIsDismissed() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)

            subject.onUiEvent(DISMISS_CLICKED)

            assertPopupVisible(visible = false)
            assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)
        }
    }

    @Test
    fun whenDisableProtectionsClickedEventIsHandledThenPopupIsDismissed() = runTest {
        subject.viewState.test {
            assertEquals(PrivacyProtectionsPopupViewState.Gone, awaitItem())
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertTrue(awaitItem() is PrivacyProtectionsPopupViewState.Visible)

            subject.onUiEvent(DISABLE_PROTECTIONS_CLICKED)

            assertEquals(PrivacyProtectionsPopupViewState.Gone, awaitItem())
            assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)
        }
    }

    @Test
    fun whenDisableProtectionsClickedEventIsHandledThenDomainIsAddedToUserAllowlist() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            assertFalse(userAllowListRepository.isUrlInUserAllowList("https://www.example.com"))
            assertPopupVisible(visible = true)

            subject.onUiEvent(DISABLE_PROTECTIONS_CLICKED)

            assertPopupVisible(visible = false)
            assertTrue(userAllowListRepository.isUrlInUserAllowList("https://www.example.com"))
        }
    }

    @Test
    fun whenPopupWasDismissedRecentlyForTheSameDomainThenItWontBeShownOnRefresh() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            subject.onUiEvent(DISMISSED)
            assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)

            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
            assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)
        }
    }

    @Test
    fun whenPopupWasDismissedMoreThan24HoursAgoForTheSameDomainThenItIsShownAgainOnRefresh() = runTest {
        subject.viewState.test {
            timeProvider.time = Instant.parse("2023-11-29T10:15:30.000Z")
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            subject.onUiEvent(DISMISSED)
            assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)
            timeProvider.time += Duration.ofDays(2)

            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)
        }
    }

    @Test
    fun whenPopupWasDismissedRecentlyThenItWontBeShownOnForTheSameDomainButWillBeForOtherDomains() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            subject.onUiEvent(DISMISSED)

            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)

            subject.onPageLoaded(url = "https://www.example2.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)

            subject.onUiEvent(DISMISSED)
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenRefreshIsTriggeredBeforeDataIsLoadedThenPopupIsNotShown() = runTest {
        val protectionsEnabledFlow = MutableSharedFlow<Boolean>()
        protectionsStateProvider.overrideProtectionsEnabledFlow(protectionsEnabledFlow)

        subject.viewState.test {
            assertPopupVisible(visible = false)
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            protectionsEnabledFlow.emit(true)
            expectNoEvents()
        }
    }

    @Test
    fun whenRefreshIsTriggeredThenPopupIsNotShownEvenIfOtherConditionsAreMetAfterAFewSeconds() = runTest {
        val protectionsEnabledFlow = MutableSharedFlow<Boolean>()
        protectionsStateProvider.overrideProtectionsEnabledFlow(protectionsEnabledFlow)

        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            timeProvider.time += Duration.ofSeconds(5)
            protectionsEnabledFlow.emit(true)
            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenToggleWasUsedInLast2WeeksThenPopupIsNotShownOnRefresh() = runTest {
        val toggleUsedAt = timeProvider.time - Duration.ofDays(10)
        dataStore.setToggleUsageTimestamp(toggleUsedAt)

        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenToggleWasNotUsedInLast2WeeksThenPopupIsShownOnRefresh() = runTest {
        val toggleUsedAt = timeProvider.time - Duration.ofDays(32)
        dataStore.setToggleUsageTimestamp(toggleUsedAt)

        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)
        }
    }

    @Test
    fun whenPageReloadsOnRefreshWithHttpErrorThenPopupIsNotDismissed() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)

            timeProvider.time += Duration.ofSeconds(2)
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = listOf(500), hasBrowserError = false)

            expectNoEvents()
        }
    }

    @Test
    fun whenPopupIsShownThenTriggerCountIsIncremented() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)
            assertEquals(1, dataStore.getPopupTriggerCount())

            subject.onUiEvent(DISMISSED)

            assertPopupVisible(visible = false)

            subject.onPageLoaded(url = "https://www.example2.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)
            assertEquals(2, dataStore.getPopupTriggerCount())
        }
    }

    @Test
    fun whenPopupTriggerCountIsZeroThenDoNotShowAgainOptionIsNotAvailable() = runTest {
        dataStore.setPopupTriggerCount(0)

        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertEquals(
                PrivacyProtectionsPopupViewState.Visible(
                    doNotShowAgainOptionAvailable = false,
                    isOmnibarAtTheTop = true,
                ),
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun whenPopupTriggerCountIsGreaterThanZeroThenDoNotShowAgainOptionIsAvailable() = runTest {
        dataStore.setPopupTriggerCount(1)

        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertEquals(
                PrivacyProtectionsPopupViewState.Visible(
                    doNotShowAgainOptionAvailable = true,
                    isOmnibarAtTheTop = true,
                ),
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun whenDoNotShowAgainIsClickedThenPopupIsNotShownAgain() = runTest {
        dataStore.setPopupTriggerCount(1)

        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertEquals(
                PrivacyProtectionsPopupViewState.Visible(
                    doNotShowAgainOptionAvailable = true,
                    isOmnibarAtTheTop = true,
                ),
                expectMostRecentItem(),
            )

            subject.onUiEvent(DONT_SHOW_AGAIN_CLICKED)

            assertPopupVisible(visible = false)
            assertTrue(dataStore.getDoNotShowAgainClicked())

            subject.onPageLoaded(url = "https://www.example2.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            expectNoEvents()
        }
    }

    @Test
    fun whenPopupConditionsAreMetAndExperimentVariantIsControlThenPopupIsNotShown() = runTest {
        dataStore.setExperimentVariant(CONTROL)
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
        }
    }

    @Test
    fun whenPopupConditionsAreMetAndExperimentVariantIsNullThenInitializesVariantWithRandomValue() = runTest {
        variantRandomizer.variant = CONTROL
        assertNull(dataStore.getExperimentVariant())

        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = false)
            assertEquals(CONTROL, dataStore.getExperimentVariant())
        }
    }

    @Test
    fun whenExperimentVariantIsAssignedThenPixelIsSent() = runTest {
        variantRandomizer.variant = CONTROL
        assertNull(dataStore.getExperimentVariant())
        var variantIncludedInPixel: PrivacyProtectionsPopupExperimentVariant? = null
        whenever(pixels.reportExperimentVariantAssigned()) doAnswer {
            variantIncludedInPixel = runBlocking { dataStore.getExperimentVariant() }
        }

        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            cancelAndIgnoreRemainingEvents()

            verify(pixels).reportExperimentVariantAssigned()
            assertEquals(CONTROL, variantIncludedInPixel) // Verify that pixel is sent AFTER assigned variant is stored.
        }
    }

    @Test
    fun whenVariantIsAlreadyAssignedThenPixelIsNotSent() = runTest {
        dataStore.setExperimentVariant(TEST)
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            assertPopupVisible(visible = true)

            verify(pixels, never()).reportExperimentVariantAssigned()
        }
    }

    @Test
    fun whenPopupIsTriggeredThenPixelIsSent() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            assertPopupVisible(visible = true)

            verify(pixels).reportPopupTriggered()
        }
    }

    @Test
    fun whenPrivacyProtectionsDisableButtonIsClickedThenPixelIsSent() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            assertPopupVisible(visible = true)

            subject.onUiEvent(DISABLE_PROTECTIONS_CLICKED)

            verify(pixels).reportProtectionsDisabled()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDismissButtonIsClickedThenPixelIsSent() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            assertPopupVisible(visible = true)

            subject.onUiEvent(DISMISS_CLICKED)

            verify(pixels).reportPopupDismissedViaButton()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPopupIsDismissedViaClickOutsideThenPixelIsSent() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            assertPopupVisible(visible = true)

            subject.onUiEvent(DISMISSED)

            verify(pixels).reportPopupDismissedViaClickOutside()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDoNotShowAgainButtonIsClickedThenPixelIsSent() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            assertPopupVisible(visible = true)

            subject.onUiEvent(DONT_SHOW_AGAIN_CLICKED)

            verify(pixels).reportDoNotShowAgainClicked()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPrivacyDashboardIsOpenedThenPixelIsSent() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)
            assertPopupVisible(visible = true)

            subject.onUiEvent(PRIVACY_DASHBOARD_CLICKED)

            verify(pixels).reportPrivacyDashboardOpened()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPageIsRefreshedAndConditionsAreMetThenPixelIsSent() = runTest {
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            verify(pixels).reportPageRefreshOnPossibleBreakage()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPageIsRefreshedAndFeatureIsDisabledAndThereIsNoExperimentVariantThenPixelIsNotSent() = runTest {
        featureFlag.self().setRawStoredState(State(enable = false))
        subject.viewState.test {
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser(true)

            verify(pixels).reportPageRefreshOnPossibleBreakage()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun ReceiveTurbine<PrivacyProtectionsPopupViewState>.assertPopupVisible(visible: Boolean) {
        if (visible) {
            assertTrue(expectMostRecentItem() is PrivacyProtectionsPopupViewState.Visible)
        } else {
            assertEquals(PrivacyProtectionsPopupViewState.Gone, expectMostRecentItem())
        }
    }

    private suspend fun assertStoredPopupDismissTimestamp(url: String, expectedTimestamp: Instant?) {
        val dismissedAt = popupDismissDomainRepository.getPopupDismissTime(url.extractDomain()!!).first()
        assertEquals(expectedTimestamp, dismissedAt)
    }
}

private class FakeProtectionsStateProvider : ProtectionsStateProvider {

    private var _protectionsEnabled = MutableStateFlow(true)

    private var protectionsEnabledOverride: Flow<Boolean>? = null

    var protectionsEnabled: Boolean
        set(value) {
            check(protectionsEnabledOverride == null)
            _protectionsEnabled.value = value
        }
        get() = _protectionsEnabled.value

    fun overrideProtectionsEnabledFlow(flow: Flow<Boolean>) {
        protectionsEnabledOverride = flow
    }

    override fun areProtectionsEnabled(domain: String): Flow<Boolean> =
        protectionsEnabledOverride ?: _protectionsEnabled.asStateFlow()
}

private class FakePopupDismissDomainRepository : PopupDismissDomainRepository {

    private val data = MutableStateFlow(emptyMap<String, Instant>())

    override fun getPopupDismissTime(domain: String): Flow<Instant?> =
        data.map { it[domain] }.distinctUntilChanged()

    override suspend fun setPopupDismissTime(
        domain: String,
        time: Instant,
    ) {
        data.update { it + (domain to time) }
    }

    override suspend fun removeEntriesOlderThan(time: Instant) =
        throw UnsupportedOperationException()

    override suspend fun removeAllEntries() =
        throw UnsupportedOperationException()
}

private class FakeDuckDuckGoUrlDetector : DuckDuckGoUrlDetector {
    override fun isDuckDuckGoUrl(url: String): Boolean = AppUrl.Url.HOST == Uri.parse(url).host

    override fun isDuckDuckGoEmailUrl(url: String): Boolean = throw UnsupportedOperationException()
    override fun isDuckDuckGoQueryUrl(uri: String): Boolean = throw UnsupportedOperationException()
    override fun isDuckDuckGoStaticUrl(uri: String): Boolean = throw UnsupportedOperationException()
    override fun extractQuery(uriString: String): String? = throw UnsupportedOperationException()
    override fun isDuckDuckGoVerticalUrl(uri: String): Boolean = throw UnsupportedOperationException()
    override fun extractVertical(uriString: String): String? = throw UnsupportedOperationException()
    override fun isDuckDuckGoChatUrl(uri: String): Boolean = throw UnsupportedOperationException()
}

private class FakePrivacyProtectionsPopupExperimentVariantRandomizer : PrivacyProtectionsPopupExperimentVariantRandomizer {
    var variant = TEST

    override fun getRandomVariant(): PrivacyProtectionsPopupExperimentVariant = variant
}
