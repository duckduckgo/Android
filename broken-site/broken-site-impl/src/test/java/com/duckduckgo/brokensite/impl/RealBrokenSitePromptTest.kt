package com.duckduckgo.brokensite.impl

import android.annotation.SuppressLint
import android.net.Uri
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@SuppressLint("DenyListedApi")
class RealBrokenSitePromptTest {

    private val mockBrokenSiteReportRepository: BrokenSiteReportRepository = mock()
    private val fakeBrokenSitePromptRCFeature: BrokenSitePromptRCFeature = FakeFeatureToggleFactory.create(BrokenSitePromptRCFeature::class.java)
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val exampleUri: Uri = mock()
    private lateinit var mockedUri: MockedStatic<Uri>

    private val testee = RealBrokenSitePrompt(
        brokenSiteReportRepository = mockBrokenSiteReportRepository,
        brokenSitePromptRCFeature = fakeBrokenSitePromptRCFeature,
        currentTimeProvider = mockCurrentTimeProvider,
        duckGoUrlDetector = mockDuckGoUrlDetector,
    )

    @Before
    fun setup() = runTest {
        mockedUri = mockStatic(Uri::class.java)
        mockedUri.`when`<Uri> { Uri.parse("https://example.com") }.thenReturn(exampleUri)
        whenever(mockBrokenSiteReportRepository.getCoolDownDays()).thenReturn(7)
        whenever(mockBrokenSiteReportRepository.getMaxDismissStreak()).thenReturn(3)
        whenever(mockBrokenSiteReportRepository.getDismissStreakResetDays()).thenReturn(30)
        whenever(mockBrokenSiteReportRepository.isRefreshPatternDetectionValid(any(), any())).thenReturn(true)
        fakeBrokenSitePromptRCFeature.self().setRawStoredState(State(true))
    }

    @After
    fun tearDown() {
        mockedUri.close()
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
    fun whenPageRefreshedAndPatternsReadThenSameOwnerAndCurrentTimestampsUsed() {
        val refreshTime = LocalDateTime.now()
        val evaluationTime = refreshTime.plusSeconds(12)
        val url: Uri = org.mockito.kotlin.mock()

        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(refreshTime, evaluationTime)
        testee.pageRefreshed(url)
        testee.getUserRefreshPatterns()

        val ownerCaptor = argumentCaptor<RefreshPatternOwner>()
        verify(mockBrokenSiteReportRepository).addRefresh(ownerCaptor.capture(), eq(url), eq(refreshTime))
        verify(mockBrokenSiteReportRepository).getRefreshPatterns(ownerCaptor.firstValue, evaluationTime)
        verify(mockCurrentTimeProvider, times(2)).localDateTimeNow()
    }

    @Test
    fun whenGetUserRefreshPatternsThenStoredPatternsReturnedAtCurrentTime() {
        val now = LocalDateTime.now()
        val patterns = setOf(RefreshPattern.TWICE_IN_12_SECONDS, RefreshPattern.THRICE_IN_20_SECONDS)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(now)
        whenever(mockBrokenSiteReportRepository.getRefreshPatterns(any(), eq(now))).thenReturn(patterns)

        assertEquals(patterns, testee.getUserRefreshPatterns())

        verify(mockCurrentTimeProvider).localDateTimeNow()
    }

    @Test
    fun whenAllRequirementsMetThenShouldShowBrokenSitePromptReturnsTrue() = runTest {
        val detectedRefreshPatterns = setOf(RefreshPattern.TWICE_IN_12_SECONDS, RefreshPattern.THRICE_IN_20_SECONDS)
        val now = LocalDateTime.now()
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(now)
        whenever(mockBrokenSiteReportRepository.getNextShownDate()).thenReturn(now.minusDays(3))
        whenever(mockBrokenSiteReportRepository.getDismissalCountBetween(any(), any())).thenReturn(2)

        val result = testee.shouldShowBrokenSitePrompt(
            "https://example.com",
            detectedRefreshPatterns,
        )
        assertTrue(result)
        verify(mockBrokenSiteReportRepository).isRefreshPatternDetectionValid(exampleUri, now)
    }

    @Test
    fun whenRefreshPatternsEmptyThenDetectionMetadataNotChecked() = runTest {
        val result = testee.shouldShowBrokenSitePrompt("https://example.com", emptySet())

        assertFalse(result)
        verify(mockBrokenSiteReportRepository, never()).isRefreshPatternDetectionValid(any(), any())
    }

    @Test
    fun whenRefreshPatternsDoNotContainThricePatternThenDetectionMetadataNotChecked() = runTest {
        val result = testee.shouldShowBrokenSitePrompt(
            "https://example.com",
            setOf(RefreshPattern.TWICE_IN_12_SECONDS),
        )

        assertFalse(result)
        verify(mockBrokenSiteReportRepository, never()).isRefreshPatternDetectionValid(any(), any())
    }

    @Test
    fun whenRefreshPatternDetectionMetadataInvalidThenShouldShowBrokenSitePromptReturnsFalse() = runTest {
        val now = LocalDateTime.now()
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(now)
        whenever(mockBrokenSiteReportRepository.isRefreshPatternDetectionValid(any(), any())).thenReturn(false)

        val result = testee.shouldShowBrokenSitePrompt(
            "https://example.com",
            setOf(RefreshPattern.THRICE_IN_20_SECONDS),
        )

        assertFalse(result)
        verify(mockBrokenSiteReportRepository, never()).getNextShownDate()
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
