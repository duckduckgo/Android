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
import app.cash.turbine.test
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISABLE_PROTECTIONS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISSED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISS_CLICKED
import com.duckduckgo.privacyprotectionspopup.impl.db.PopupDismissDomainRepository
import com.duckduckgo.privacyprotectionspopup.impl.db.ToggleUsageTimestampRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.threeten.bp.Duration
import org.threeten.bp.Instant

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PrivacyProtectionsPopupManagerImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val featureAvailability = FakePrivacyProtectionsPopupFeatureAvailability()

    private val protectionsStateProvider = FakeProtectionsStateProvider()

    private val timeProvider = FakeTimeProvider()

    private val popupDismissDomainRepository = FakePopupDismissDomainRepository()

    private val userAllowListRepository = FakeUserAllowlistRepository()

    private val toggleUsageTimestampRepository = FakeToggleUsageTimestampRepository()

    private val duckDuckGoUrlDetector = FakeDuckDuckGoUrlDetector()

    private val subject = PrivacyProtectionsPopupManagerImpl(
        appCoroutineScope = coroutineRule.testScope,
        featureAvailability = featureAvailability,
        protectionsStateProvider = protectionsStateProvider,
        timeProvider = timeProvider,
        popupDismissDomainRepository = popupDismissDomainRepository,
        userAllowListRepository = userAllowListRepository,
        toggleUsageTimestampRepository = toggleUsageTimestampRepository,
        duckDuckGoUrlDetector = duckDuckGoUrlDetector,
    )

    @Test
    fun whenRefreshIsTriggeredThenEmitsUpdateToShowPopup() = runTest {
        val toggleUsedAt = timeProvider.time - Duration.ofDays(17)
        toggleUsageTimestampRepository.setToggleUsageTimestamp(toggleUsedAt)

        subject.viewState.test {
            assertFalse(awaitItem().visible)
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            expectNoEvents()
            subject.onPageRefreshTriggeredByUser()
            assertTrue(awaitItem().visible)
            expectNoEvents()
        }
    }

    @Test
    fun whenRefreshIsTriggeredThenPopupIsShown() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)
    }

    @Test
    fun whenUrlIsDuckDuckGoThenPopupIsNotShown() = runTest {
        subject.onPageLoaded(url = "https://duckduckgo.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenFeatureIsDisabledThenPopupIsNotShown() = runTest {
        featureAvailability.featureAvailable = false
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenProtectionsAreDisabledThenPopupIsNotShown() = runTest {
        protectionsStateProvider.protectionsEnabled = false
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenUrlIsMissingThenPopupIsNotShown() = runTest {
        subject.onPageLoaded(url = "", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenPageLoadedWithHttpErrorThenPopupIsNotShown() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = listOf(500), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenPageLoadedWithBrowserErrorThenPopupIsNotShown() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = true)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenPageIsChangedThenPopupIsDismissed() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)

        subject.onPageLoaded(url = "https://www.example2.com", httpErrorCodes = emptyList(), hasBrowserError = false)

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenDismissEventIsHandledThenViewStateIsUpdated() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)

        subject.onUiEvent(DISMISSED)

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenDismissButtonClickedEventIsHandledThenPopupIsDismissed() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)

        subject.onUiEvent(DISMISS_CLICKED)

        assertPopupVisible(visible = false)
        assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)
    }

    @Test
    fun whenDisableProtectionsClickedEventIsHandledThenPopupIsDismissed() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)

        subject.onUiEvent(DISABLE_PROTECTIONS_CLICKED)

        assertPopupVisible(visible = false)
        assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)
    }

    @Test
    fun whenDisableProtectionsClickedEventIsHandledThenDomainIsAddedToUserAllowlist() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()
        assertFalse(userAllowListRepository.isUrlInUserAllowList("https://www.example.com"))
        assertPopupVisible(visible = true)

        subject.onUiEvent(DISABLE_PROTECTIONS_CLICKED)

        assertPopupVisible(visible = false)
        assertTrue(userAllowListRepository.isUrlInUserAllowList("https://www.example.com"))
    }

    @Test
    fun whenPopupWasDismissedRecentlyForTheSameDomainThenItWontBeShownOnRefresh() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()
        subject.onUiEvent(DISMISSED)
        assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)

        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
        assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)
    }

    @Test
    fun whenPopupWasDismissedMoreThan24HoursAgoForTheSameDomainThenItIsShownAgainOnRefresh() = runTest {
        timeProvider.time = Instant.parse("2023-11-29T10:15:30.000Z")
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()
        subject.onUiEvent(DISMISSED)
        assertStoredPopupDismissTimestamp(url = "https://www.example.com", expectedTimestamp = timeProvider.time)
        timeProvider.time += Duration.ofHours(24)

        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)
    }

    @Test
    fun whenPopupWasDismissedRecentlyThenItWontBeShownOnForTheSameDomainButWillBeForOtherDomains() = runTest {
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()
        subject.onUiEvent(DISMISSED)

        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)

        subject.onPageLoaded(url = "https://www.example2.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)

        subject.onUiEvent(DISMISSED)
        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenRefreshIsTriggeredThenPopupIsNotShownUntilOtherConditionsAreMet() = runTest {
        val protectionsEnabledFlow = MutableSharedFlow<Boolean>()
        protectionsStateProvider.overrideProtectionsEnabledFlow(protectionsEnabledFlow)

        subject.viewState.test {
            assertFalse(awaitItem().visible)
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser()
            expectNoEvents()
            protectionsEnabledFlow.emit(true)
            assertTrue(awaitItem().visible)
        }
    }

    @Test
    fun whenRefreshIsTriggeredThenPopupIsNotShownEvenIfOtherConditionsAreMetAfterAFewSeconds() = runTest {
        val protectionsEnabledFlow = MutableSharedFlow<Boolean>()
        protectionsStateProvider.overrideProtectionsEnabledFlow(protectionsEnabledFlow)

        subject.viewState.test {
            assertFalse(awaitItem().visible)
            subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
            subject.onPageRefreshTriggeredByUser()
            expectNoEvents()
            timeProvider.time += Duration.ofSeconds(5)
            protectionsEnabledFlow.emit(true)
            expectNoEvents()
        }

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenToggleWasUsedInLast2WeeksThenPopupIsNotShownOnRefresh() = runTest {
        val toggleUsedAt = timeProvider.time - Duration.ofDays(10)
        toggleUsageTimestampRepository.setToggleUsageTimestamp(toggleUsedAt)

        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = false)
    }

    @Test
    fun whenToggleWasNotUsedInLast2WeeksThenPopupIsShownOnRefresh() = runTest {
        val toggleUsedAt = timeProvider.time - Duration.ofDays(15)
        toggleUsageTimestampRepository.setToggleUsageTimestamp(toggleUsedAt)

        subject.onPageLoaded(url = "https://www.example.com", httpErrorCodes = emptyList(), hasBrowserError = false)
        subject.onPageRefreshTriggeredByUser()

        assertPopupVisible(visible = true)
    }

    private suspend fun assertPopupVisible(visible: Boolean) {
        subject.viewState.test {
            assertEquals(visible, awaitItem().visible)
            expectNoEvents()
        }
    }

    private suspend fun assertStoredPopupDismissTimestamp(url: String, expectedTimestamp: Instant?) {
        val dismissedAt = popupDismissDomainRepository.getPopupDismissTime(url.extractDomain()!!).first()
        assertEquals(expectedTimestamp, dismissedAt)
    }
}

private class FakePrivacyProtectionsPopupFeatureAvailability : PrivacyProtectionsPopupFeatureAvailability {

    var featureAvailable = true

    override suspend fun isAvailable(): Boolean = featureAvailable
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
}

private class FakeToggleUsageTimestampRepository : ToggleUsageTimestampRepository {

    private val timestamp = MutableStateFlow<Instant?>(value = null)

    override fun getToggleUsageTimestamp(): Flow<Instant?> = timestamp

    override suspend fun setToggleUsageTimestamp(timestamp: Instant) {
        this.timestamp.value = timestamp
    }
}

private class FakeDuckDuckGoUrlDetector : DuckDuckGoUrlDetector {
    override fun isDuckDuckGoUrl(url: String): Boolean = AppUrl.Url.HOST == Uri.parse(url).host

    override fun isDuckDuckGoEmailUrl(url: String): Boolean = throw UnsupportedOperationException()
    override fun isDuckDuckGoQueryUrl(uri: String): Boolean = throw UnsupportedOperationException()
    override fun isDuckDuckGoStaticUrl(uri: String): Boolean = throw UnsupportedOperationException()
    override fun extractQuery(uriString: String): String? = throw UnsupportedOperationException()
    override fun isDuckDuckGoVerticalUrl(uri: String): Boolean = throw UnsupportedOperationException()
    override fun extractVertical(uriString: String): String? = throw UnsupportedOperationException()
}
