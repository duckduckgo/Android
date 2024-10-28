package com.duckduckgo.brokensite.impl

import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealBrokenSitePromptTest {

    private val mockBrokenSiteReportRepository: BrokenSiteReportRepository = mock()
    private val mockBrokenSitePromptRCFeature: BrokenSitePromptRCFeature = FakeFeatureToggleFactory.create(BrokenSitePromptRCFeature::class.java)

    private val testee = RealBrokenSitePrompt(
        brokenSiteReportRepository = mockBrokenSiteReportRepository,
        brokenSitePromptRCFeature = mockBrokenSitePromptRCFeature,
    )

    @Before
    fun setup() {
        whenever(mockBrokenSiteReportRepository.getCoolDownDays()).thenReturn(7)
        whenever(mockBrokenSiteReportRepository.getMaxDismissStreak()).thenReturn(3)
        mockBrokenSitePromptRCFeature.self().setRawStoredState(State(true))
    }

    @Test
    fun whenUserDismissedPromptAndNoNextShownDateThenIncrementDismissStreakAndDoNotUpdateNextShownDate() = runTest {
        whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(null)

        testee.userDismissedPrompt()

        verify(mockBrokenSiteReportRepository, never()).setNextShownDate(any())
        verify(mockBrokenSiteReportRepository).incrementDismissStreak()
    }

    @Test
    fun whenUserDismissedPromptMaxDismissStreakTimesAndNextShownDateEarlierThanCooldownDaysThenIncrementDismissStreakAndUpdateNextShownDate() = runTest {
        whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(LocalDate.now().plusDays(5))
        whenever(mockBrokenSiteReportRepository.getDismissStreak()).thenReturn(2)

        testee.userDismissedPrompt()

        verify(mockBrokenSiteReportRepository).setNextShownDate(LocalDate.now().plusDays(7))
        verify(mockBrokenSiteReportRepository).incrementDismissStreak()
    }

    @Test
    fun whenUserDismissedPromptMaxDismissStreakTimesAndNextShownDatLaterThanCooldownDaysThenIncrementDismissStreakAndDoNotUpdateNextShownDate() = runTest {
        whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(LocalDate.now().plusDays(11))
        whenever(mockBrokenSiteReportRepository.getDismissStreak()).thenReturn(2)

        testee.userDismissedPrompt()

        verify(mockBrokenSiteReportRepository, never()).setNextShownDate(any())
        verify(mockBrokenSiteReportRepository).incrementDismissStreak()
    }

    @Test
    fun whenUserDismissedPromptLessThanMaxDismissStreakTimesAndNextShownDateEarlierThanCooldownDaysThenIncrementDismissStreakAndDoNotUpdateNextShownDate() = runTest {
        whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(LocalDate.now().plusDays(5))
        whenever(mockBrokenSiteReportRepository.getDismissStreak()).thenReturn(0)

        testee.userDismissedPrompt()

        verify(mockBrokenSiteReportRepository, never()).setNextShownDate(any())
        verify(mockBrokenSiteReportRepository).incrementDismissStreak()
    }

    @Test
    fun whenUserDismissedPromptAndFeatureDisabledThenDoNothing() = runTest {
        mockBrokenSitePromptRCFeature.self().setRawStoredState(State(false))

        testee.userDismissedPrompt()

        verify(mockBrokenSiteReportRepository, never()).setNextShownDate(any())
        verify(mockBrokenSiteReportRepository, never()).incrementDismissStreak()
    }

    @Test
    fun whenUserAcceptedPromptThenResetDismissStreakAndSetNextShownDateToNull() = runTest {
        testee.userAcceptedPrompt()

        verify(mockBrokenSiteReportRepository).resetDismissStreak()
        verify(mockBrokenSiteReportRepository).setNextShownDate(null)
    }

    @Test
    fun whenUserAcceptedPromptAndFeatureDisabledThenDoNothing() = runTest {
        mockBrokenSitePromptRCFeature.self().setRawStoredState(State(false))

        testee.userAcceptedPrompt()

        verify(mockBrokenSiteReportRepository, never()).resetDismissStreak()
        verify(mockBrokenSiteReportRepository, never()).setNextShownDate(any())
    }
}
