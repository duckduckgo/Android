package com.duckduckgo.app.browser.refreshpixels

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.LOADING_BAR_EXPERIMENT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.experiments.api.loadingbarexperiment.LoadingBarExperimentManager
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RefreshPixelSenderTest {

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var refreshDao: RefreshDao
    private val mockPixel: Pixel = mock()
    private val mockLoadingBarExperimentManager: LoadingBarExperimentManager = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    private lateinit var testee: DuckDuckGoRefreshPixelSender

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        refreshDao = db.refreshDao()

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(CURRENT_TIME)

        testee = DuckDuckGoRefreshPixelSender(
            pixel = mockPixel,
            dao = refreshDao,
            loadingBarExperimentManager = mockLoadingBarExperimentManager,
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenSendMenuRefreshPixelsAndExperimentEnabledAndIsTestVariantThenTestVariantPixelsFired() {
        whenever(mockLoadingBarExperimentManager.isExperimentEnabled()).thenReturn(true)
        whenever(mockLoadingBarExperimentManager.variant).thenReturn(true)

        testee.sendMenuRefreshPixels()

        verify(mockPixel).fire(
            pixel = AppPixelName.MENU_ACTION_REFRESH_PRESSED,
            parameters = mapOf(LOADING_BAR_EXPERIMENT to "1"),
        )
        verify(mockPixel).fire(
            pixel = AppPixelName.REFRESH_ACTION_DAILY_PIXEL,
            parameters = mapOf(LOADING_BAR_EXPERIMENT to "1"),
            type = Daily(),
        )
    }

    @Test
    fun whenSendMenuRefreshPixelsAndExperimentEnabledAndIsControlVariantThenControlVariantPixelsFired() {
        whenever(mockLoadingBarExperimentManager.isExperimentEnabled()).thenReturn(true)
        whenever(mockLoadingBarExperimentManager.variant).thenReturn(false)

        testee.sendMenuRefreshPixels()

        verify(mockPixel).fire(
            pixel = AppPixelName.MENU_ACTION_REFRESH_PRESSED,
            parameters = mapOf(LOADING_BAR_EXPERIMENT to "0"),
        )
        verify(mockPixel).fire(
            pixel = AppPixelName.REFRESH_ACTION_DAILY_PIXEL,
            parameters = mapOf(LOADING_BAR_EXPERIMENT to "0"),
            type = Daily(),
        )
    }

    @Test
    fun whenSendMenuRefreshPixelsAndExperimentDisabledThenDefaultPixelsFired() {
        whenever(mockLoadingBarExperimentManager.isExperimentEnabled()).thenReturn(false)

        testee.sendMenuRefreshPixels()

        verify(mockPixel).fire(
            pixel = AppPixelName.MENU_ACTION_REFRESH_PRESSED,
        )
        verify(mockPixel).fire(
            pixel = AppPixelName.REFRESH_ACTION_DAILY_PIXEL,
            type = Daily(),
        )
    }

    @Test
    fun whenSendPullToRefreshPixelsAndExperimentEnabledAndIsTestVariantThenTestVariantPixelsFired() {
        whenever(mockLoadingBarExperimentManager.isExperimentEnabled()).thenReturn(true)
        whenever(mockLoadingBarExperimentManager.variant).thenReturn(true)

        testee.sendPullToRefreshPixels()

        verify(mockPixel).fire(
            pixel = AppPixelName.BROWSER_PULL_TO_REFRESH,
            parameters = mapOf(LOADING_BAR_EXPERIMENT to "1"),
        )
        verify(mockPixel).fire(
            pixel = AppPixelName.REFRESH_ACTION_DAILY_PIXEL,
            parameters = mapOf(LOADING_BAR_EXPERIMENT to "1"),
            type = Daily(),
        )
    }

    @Test
    fun whenSendPullToRefreshPixelsAndExperimentEnabledAndIsControlVariantThenControlVariantPixelsFired() {
        whenever(mockLoadingBarExperimentManager.isExperimentEnabled()).thenReturn(true)
        whenever(mockLoadingBarExperimentManager.variant).thenReturn(false)

        testee.sendPullToRefreshPixels()

        verify(mockPixel).fire(
            pixel = AppPixelName.BROWSER_PULL_TO_REFRESH,
            parameters = mapOf(LOADING_BAR_EXPERIMENT to "0"),
        )
        verify(mockPixel).fire(
            pixel = AppPixelName.REFRESH_ACTION_DAILY_PIXEL,
            parameters = mapOf(LOADING_BAR_EXPERIMENT to "0"),
            type = Daily(),
        )
    }

    @Test
    fun whenSendPullToRefreshPixelsAndExperimentDisabledThenDefaultPixelsFired() {
        whenever(mockLoadingBarExperimentManager.isExperimentEnabled()).thenReturn(false)

        testee.sendPullToRefreshPixels()

        verify(mockPixel).fire(
            pixel = AppPixelName.BROWSER_PULL_TO_REFRESH,
        )
        verify(mockPixel).fire(
            pixel = AppPixelName.REFRESH_ACTION_DAILY_PIXEL,
            type = Daily(),
        )
    }

    @Test
    fun whenSendCustomTabRefreshPixelThenCorrectPixelFired() {
        testee.sendCustomTabRefreshPixel()

        verify(mockPixel).fire(CustomTabPixelNames.CUSTOM_TABS_MENU_REFRESH)
    }

    @Test
    fun whenSendMenuRefreshPixelsThenTimeBasedPixelsFired() = runTest {
        val mockDao: RefreshDao = mock()

        testee = DuckDuckGoRefreshPixelSender(
            pixel = mockPixel,
            dao = mockDao,
            loadingBarExperimentManager = mockLoadingBarExperimentManager,
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )

        testee.sendMenuRefreshPixels()

        verify(mockDao).updateRecentRefreshes(CURRENT_TIME - 20000, CURRENT_TIME)
    }

    @Test
    fun whenSendPullToRefreshPixelsThenTimeBasedPixelsFired() = runTest {
        val mockDao: RefreshDao = mock()

        testee = DuckDuckGoRefreshPixelSender(
            pixel = mockPixel,
            dao = mockDao,
            loadingBarExperimentManager = mockLoadingBarExperimentManager,
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )

        testee.sendPullToRefreshPixels()

        verify(mockDao).updateRecentRefreshes(CURRENT_TIME - 20000, CURRENT_TIME)
    }

    @Test
    fun whenSendCustomTabRefreshPixelThenTimeBasedPixelsFired() = runTest {
        val mockDao: RefreshDao = mock()

        testee = DuckDuckGoRefreshPixelSender(
            pixel = mockPixel,
            dao = mockDao,
            loadingBarExperimentManager = mockLoadingBarExperimentManager,
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )

        testee.sendCustomTabRefreshPixel()

        verify(mockDao).updateRecentRefreshes(CURRENT_TIME - 20000, CURRENT_TIME)
    }

    @Test
    fun whenSendTimeBasedPixelsAndNoRecentRefreshesThenNoPixelsFired() = runTest {
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 75000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 50000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 25000))

        testee.sendMenuRefreshPixels()

        verify(mockPixel, never()).fire(AppPixelName.RELOAD_TWICE_WITHIN_12_SECONDS)
        verify(mockPixel, never()).fire(AppPixelName.RELOAD_THREE_TIMES_WITHIN_20_SECONDS)
        assertTrue(refreshDao.all().size == 1)
    }

    @Test
    fun whenSendTimeBasedPixelsAndTwoRefreshesWithinTwelveSecondsThenReloadTwicePixelFired() = runTest {
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 75000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 50000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 11000))

        testee.sendMenuRefreshPixels()

        verify(mockPixel).fire(AppPixelName.RELOAD_TWICE_WITHIN_12_SECONDS)
        verify(mockPixel, never()).fire(AppPixelName.RELOAD_THREE_TIMES_WITHIN_20_SECONDS)
        assertTrue(refreshDao.all().size == 2)
    }

    @Test
    fun whenSendTimeBasedPixelsAndThreeRefreshesWithinTwentySecondsThenReloadThricePixelFired() = runTest {
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 75000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 50000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 19000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 15000))

        testee.sendMenuRefreshPixels()

        verify(mockPixel, never()).fire(AppPixelName.RELOAD_TWICE_WITHIN_12_SECONDS)
        verify(mockPixel).fire(AppPixelName.RELOAD_THREE_TIMES_WITHIN_20_SECONDS)
        assertTrue(refreshDao.all().size == 3)
    }

    @Test
    fun whenSendTimeBasedPixelsAndThreeRefreshesWithinTwentySecondsAndTwoRefreshesWithinTwelveSecondsThenBothPixelsFired() = runTest {
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 75000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 50000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 19000))
        refreshDao.insert(RefreshEntity(timestamp = CURRENT_TIME - 5000))

        testee.sendMenuRefreshPixels()

        verify(mockPixel).fire(AppPixelName.RELOAD_TWICE_WITHIN_12_SECONDS)
        verify(mockPixel).fire(AppPixelName.RELOAD_THREE_TIMES_WITHIN_20_SECONDS)
        assertTrue(refreshDao.all().size == 3)
    }

    companion object {
        private const val CURRENT_TIME = 100000L
    }
}
