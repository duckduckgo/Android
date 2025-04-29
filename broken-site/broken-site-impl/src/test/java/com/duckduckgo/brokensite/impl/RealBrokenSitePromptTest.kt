package com.duckduckgo.brokensite.impl

import android.annotation.SuppressLint
import android.net.Uri
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealBrokenSitePromptTest {

    private val mockBrokenSiteReportRepository: BrokenSiteReportRepository = mock()
    private val fakeBrokenSitePromptRCFeature: BrokenSitePromptRCFeature = FakeFeatureToggleFactory.create(BrokenSitePromptRCFeature::class.java)
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()

    private val testee = RealBrokenSitePrompt(
        brokenSiteReportRepository = mockBrokenSiteReportRepository,
        brokenSitePromptRCFeature = fakeBrokenSitePromptRCFeature,
        currentTimeProvider = mockCurrentTimeProvider,
        duckGoUrlDetector = mockDuckGoUrlDetector,
    )

    @Before
    fun setup() = runTest {
        whenever(mockBrokenSiteReportRepository.getCoolDownDays()).thenReturn(7)
        whenever(mockBrokenSiteReportRepository.getMaxDismissStreak()).thenReturn(3)
        whenever(mockBrokenSiteReportRepository.getDismissStreakResetDays()).thenReturn(30)
        fakeBrokenSitePromptRCFeature.self().setRawStoredState(State(true))
    }

    @Test
    fun whenUserDismissedPromptThenAddDismissal() = runTest {
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())

        testee.userDismissedPrompt()

        verify(mockBrokenSiteReportRepository).addDismissal(any())
    }

    @Test
    fun whenUserDismissedPromptAndFeatureDisabledThenDoNothing() = runTest {
        fakeBrokenSitePromptRCFeature.self().setRawStoredState(State(false))

        testee.userDismissedPrompt()

        verify(mockBrokenSiteReportRepository, never()).addDismissal(any())
    }

    @Test
    fun whenUserAcceptedPromptThenClearAllStoredDismissals() = runTest {
        testee.userAcceptedPrompt()

        verify(mockBrokenSiteReportRepository).clearAllDismissals()
    }

    @Test
    fun whenUserAcceptedPromptAndFeatureDisabledThenDoNothing() = runTest {
        fakeBrokenSitePromptRCFeature.self().setRawStoredState(State(false))

        testee.userAcceptedPrompt()

        verify(mockBrokenSiteReportRepository, never()).clearAllDismissals()
    }

    @Test
    fun whenIncrementRefreshCountThenAddRefreshCalled() {
        val now = LocalDateTime.now()
        val url: Uri = org.mockito.kotlin.mock()

        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(now)
        testee.pageRefreshed(url)

        verify(mockBrokenSiteReportRepository).addRefresh(url, now)
    }

    @Test
    fun whenGetUserRefreshPatternsThenGetRefreshPatternsCalled() {
        val now = LocalDateTime.now()
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(now)

        testee.getUserRefreshPatterns()

        verify(mockBrokenSiteReportRepository).getRefreshPatterns(now)
    }

    @Test
    fun whenAllRequirementsMetThenShouldShowBrokenSitePromptReturnsTrue() = runTest {
        val detectedRefreshPatterns = setOf(RefreshPattern.TWICE_IN_12_SECONDS, RefreshPattern.THRICE_IN_20_SECONDS)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())
        whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(LocalDateTime.now().minusDays(3))
        whenever(mockBrokenSiteReportRepository.getDismissalCountBetween(any(), any())).thenReturn(2)

        val result = testee.shouldShowBrokenSitePrompt(
            "https://example.com",
            detectedRefreshPatterns,
        )
        assertTrue(result)
    }

    @Test
    fun whenFeatureEnabledAndUrlIsDuckDuckGoThenShouldShowBrokenSitePromptReturnsFalse() = runTest {
        val detectedRefreshPatterns = setOf(RefreshPattern.TWICE_IN_12_SECONDS, RefreshPattern.THRICE_IN_20_SECONDS)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())
        whenever(mockDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(true)

        val result = testee.shouldShowBrokenSitePrompt(
            "https://duckduckgo.com",
            detectedRefreshPatterns,
        )

        assertFalse(result)
    }

    @Test
    fun whenFeatureEnabledAndUserStillInCooldownPeriodThenShouldShowBrokenSitePromptReturnsFalse() = runTest {
        val detectedRefreshPatterns = setOf(RefreshPattern.TWICE_IN_12_SECONDS, RefreshPattern.THRICE_IN_20_SECONDS)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())
        whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(LocalDateTime.now().plusDays(3))

        val result = testee.shouldShowBrokenSitePrompt(
            "https://example.com",
            detectedRefreshPatterns,
        )

        assertFalse(result)
    }

    @Test
    fun whenFeatureEnabledAndUserHasDismissedMaxDismissStreakTimesThenShouldShowBrokenSitePromptReturnsFalse() = runTest {
        val detectedRefreshPatterns = setOf(RefreshPattern.TWICE_IN_12_SECONDS, RefreshPattern.THRICE_IN_20_SECONDS)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())
        whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(LocalDateTime.now().minusDays(3))
        whenever(mockBrokenSiteReportRepository.getDismissalCountBetween(any(), any())).thenReturn(3)

        val result = testee.shouldShowBrokenSitePrompt(
            "https://example.com",
            detectedRefreshPatterns,
        )

        assertFalse(result)
    }

    @Test
    fun whenFeatureDisabledThenShouldShowBrokenSitePromptReturnsFalse() = runTest {
        val detectedRefreshPatterns = setOf(RefreshPattern.TWICE_IN_12_SECONDS, RefreshPattern.THRICE_IN_20_SECONDS)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())
        fakeBrokenSitePromptRCFeature.self().setRawStoredState(State(false))

        val result = testee.shouldShowBrokenSitePrompt(
            "https://example.com",
            detectedRefreshPatterns,
        )

        assertFalse(result)
    }
}
