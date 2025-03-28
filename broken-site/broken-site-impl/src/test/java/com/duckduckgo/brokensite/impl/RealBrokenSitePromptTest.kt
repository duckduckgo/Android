package com.duckduckgo.brokensite.impl

import android.annotation.SuppressLint
import android.net.Uri
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
    fun whenUserDismissedPromptMaxDismissStreakTimesAndNoNextShownDateThenAddDismissalAndUpdateNextShownDate() = runTest {
        whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(null)
        whenever(mockBrokenSiteReportRepository.getDismissalCountBetween(any(), any())).thenReturn(3)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())

        testee.userDismissedPrompt()

        verify(mockBrokenSiteReportRepository).setNextShownDate(any())
        verify(mockBrokenSiteReportRepository).addDismissal(any())
    }

    @Test
    fun whenUserDismissedPromptMaxDismissStreakTimesAndNextShownDateEarlierThanDismissStreakDaysThenAddDismissalAndUpdateNextShownDate() =
        runTest {
            whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(LocalDateTime.now().plusDays(5))
            whenever(mockBrokenSiteReportRepository.getDismissalCountBetween(any(), any())).thenReturn(3)
            whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())

            testee.userDismissedPrompt()

            val argumentCaptor = argumentCaptor<LocalDateTime>()
            verify(mockBrokenSiteReportRepository).setNextShownDate(argumentCaptor.capture())
            assertEquals(LocalDateTime.now().plusDays(30).toLocalDate(), argumentCaptor.firstValue.toLocalDate())
            verify(mockBrokenSiteReportRepository).addDismissal(any())
        }

    @Test
    fun whenUserDismissedPromptMaxDismissStreakTimesAndNextShownDateLaterThanDismissStreakDaysThenAddDismissalAndDoNotUpdateNextShownDate() =
        runTest {
            whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(LocalDateTime.now().plusDays(11))
            whenever(mockBrokenSiteReportRepository.getDismissalCountBetween(any(), any())).thenReturn(3)
            whenever(mockBrokenSiteReportRepository.getDismissStreakResetDays().toLong()).thenReturn(2)
            whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())

            testee.userDismissedPrompt()

            verify(mockBrokenSiteReportRepository, never()).setNextShownDate(any())
            verify(mockBrokenSiteReportRepository).addDismissal(any())
        }

    @Test
    fun whenUserDismissPromptLessThanMaxDismissStreakTimesThenAddDismissalAndDoNotUpdateNextShownDate() =
        runTest {
            whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())
            whenever(mockBrokenSiteReportRepository.getDismissalCountBetween(any(), any())).thenReturn(1)

            testee.userDismissedPrompt()

            verify(mockBrokenSiteReportRepository, never()).setNextShownDate(any())
            verify(mockBrokenSiteReportRepository).addDismissal(any())
        }

    @Test
    fun whenUserDismissedPromptAndFeatureDisabledThenDoNothing() = runTest {
        fakeBrokenSitePromptRCFeature.self().setRawStoredState(State(false))

        testee.userDismissedPrompt()

        verify(mockBrokenSiteReportRepository, never()).setNextShownDate(any())
        verify(mockBrokenSiteReportRepository, never()).addDismissal(any())
    }

    @Test
    fun whenUserAcceptedPromptThenResetDismissStreak() = runTest {
        testee.userAcceptedPrompt()

        verify(mockBrokenSiteReportRepository).resetDismissStreak()
    }

    @Test
    fun whenUserAcceptedPromptAndFeatureDisabledThenDoNothing() = runTest {
        fakeBrokenSitePromptRCFeature.self().setRawStoredState(State(false))

        testee.userAcceptedPrompt()

        verify(mockBrokenSiteReportRepository, never()).resetDismissStreak()
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
    fun whenResetRefreshCountThenResetRefreshCountCalled() {
        testee.resetRefreshCount()

        verify(mockBrokenSiteReportRepository).resetRefreshCount()
    }

    @Test
    fun whenGetUserRefreshesCountThenGetAndUpdateUserRefreshesBetweenCalled() {
        val now = LocalDateTime.now()
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(now)

        testee.getUserRefreshesCount()

        verify(mockBrokenSiteReportRepository).getAndUpdateUserRefreshesBetween(now.minusSeconds(REFRESH_COUNT_WINDOW), now)
    }

    @Test
    fun whenFeatureEnabledAndUserRefreshesCountIsThreeOrMoreThenShouldShowBrokenSitePromptReturnsTrue() = runTest {
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())
        whenever(mockBrokenSiteReportRepository.getAndUpdateUserRefreshesBetween(any(), any())).thenReturn(REFRESH_COUNT_LIMIT)

        val result = testee.shouldShowBrokenSitePrompt("https://example.com")

        assertTrue(result)
    }

    @Test
    fun whenFeatureEnabledAndUserRefreshesCountIsLessThanThreeThenShouldShowBrokenSitePromptReturnsFalse() = runTest {
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())
        whenever(mockBrokenSiteReportRepository.getAndUpdateUserRefreshesBetween(any(), any())).thenReturn(2)

        val result = testee.shouldShowBrokenSitePrompt("https://example.com")

        assertFalse(result)
    }

    @Test
    fun whenFeatureDisabledThenShouldShowBrokenSitePromptReturnsFalse() = runTest {
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.now())
        fakeBrokenSitePromptRCFeature.self().setRawStoredState(State(false))

        val result = testee.shouldShowBrokenSitePrompt("https://example.com")

        assertFalse(result)
    }
}
