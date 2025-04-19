package com.duckduckgo.app.browser.refreshpixels

import android.annotation.SuppressLint
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.trackerdetection.blocklist.BlockList.Cohorts.TREATMENT
import com.duckduckgo.app.trackerdetection.blocklist.BlockListPixelsPlugin
import com.duckduckgo.app.trackerdetection.blocklist.FakeFeatureTogglesInventory
import com.duckduckgo.app.trackerdetection.blocklist.TestBlockListFeature
import com.duckduckgo.app.trackerdetection.blocklist.get2XRefresh
import com.duckduckgo.app.trackerdetection.blocklist.get3XRefresh
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import com.squareup.moshi.Moshi
import java.time.ZoneId
import java.time.ZonedDateTime
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class RefreshPixelSenderTest {

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private val moshi = Moshi.Builder().build()

    private data class Config(
        val treatmentUrl: String? = null,
        val controlUrl: String? = null,
        val nextUrl: String? = null,
    )
    private val configAdapter = moshi.adapter(Config::class.java)

    private lateinit var db: AppDatabase
    private lateinit var refreshDao: RefreshDao
    private val mockPixel: Pixel = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private lateinit var testBlockListFeature: TestBlockListFeature
    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var blockListPixelsPlugin: BlockListPixelsPlugin

    private lateinit var testee: DuckDuckGoRefreshPixelSender

    @Before
    fun setUp() {
        testBlockListFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "blockList",
        ).build().create(TestBlockListFeature::class.java)

        inventory = RealFeatureTogglesInventory(
            setOf(
                FakeFeatureTogglesInventory(
                    features = listOf(
                        testBlockListFeature.tdsNextExperimentTest(),
                        testBlockListFeature.tdsNextExperimentAnotherTest(),
                    ),
                ),
            ),
            coroutineTestRule.testDispatcherProvider,
        )

        blockListPixelsPlugin = BlockListPixelsPlugin(inventory)

        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        refreshDao = db.refreshDao()

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(CURRENT_TIME)

        testee = DuckDuckGoRefreshPixelSender(
            pixel = mockPixel,
            dao = refreshDao,
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            blockListPixelsPlugin = BlockListPixelsPlugin(inventory),
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenSendMenuRefreshPixelsThenPixelsFired() {
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
    fun whenSendPullToRefreshPixelsThenPixelsFired() {
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
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            blockListPixelsPlugin = blockListPixelsPlugin,
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
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            blockListPixelsPlugin = blockListPixelsPlugin,
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
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            blockListPixelsPlugin = blockListPixelsPlugin,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )

        testee.sendCustomTabRefreshPixel()

        verify(mockDao).updateRecentRefreshes(CURRENT_TIME - 20000, CURRENT_TIME)
    }

    @Test
    fun whenRefreshedOnceThenNoPixelFired() = runTest {
        testee.sendMenuRefreshPixels()

        verify(mockPixel, never()).fire(AppPixelName.RELOAD_TWICE_WITHIN_12_SECONDS)
        verify(mockPixel, never()).fire(AppPixelName.RELOAD_THREE_TIMES_WITHIN_20_SECONDS)
        assertTrue(refreshDao.all().size == 1)
    }

    @Test
    fun whenRefreshedTwiceThenReloadTwicePixelFired() = runTest {
        testee.sendMenuRefreshPixels()
        testee.sendMenuRefreshPixels()

        verify(mockPixel).fire(AppPixelName.RELOAD_TWICE_WITHIN_12_SECONDS)
        verify(mockPixel, never()).fire(AppPixelName.RELOAD_THREE_TIMES_WITHIN_20_SECONDS)
        assertNull(blockListPixelsPlugin.get2XRefresh())
        assertTrue(refreshDao.all().size == 2)
    }

    @Test
    fun whenRefreshedTwiceAndAssignedToExperimentThen2XRefreshPixelsFired() = runTest {
        assignToExperiment()
        testee.sendMenuRefreshPixels()
        testee.sendMenuRefreshPixels()

        blockListPixelsPlugin.get2XRefresh()!!.getPixelDefinitions().forEach {
            verify(mockPixel).fire(it.pixelName, it.params)
        }
    }

    @Test
    fun whenRefreshedThreeTimesThenReloadTwicePixelFiredTwiceAndReloadThricePixelFired() = runTest {
        testee.sendMenuRefreshPixels()
        testee.sendMenuRefreshPixels()
        testee.sendMenuRefreshPixels()

        verify(mockPixel, times(2)).fire(AppPixelName.RELOAD_TWICE_WITHIN_12_SECONDS)
        verify(mockPixel).fire(AppPixelName.RELOAD_THREE_TIMES_WITHIN_20_SECONDS)
        assertNull(blockListPixelsPlugin.get3XRefresh())
        assertTrue(refreshDao.all().size == 3)
    }

    @Test
    fun whenRefreshedThreeTimesAndAssignedToExperimentThen3XRefreshPixelsFired() = runTest {
        assignToExperiment()
        testee.sendMenuRefreshPixels()
        testee.sendMenuRefreshPixels()
        testee.sendMenuRefreshPixels()

        blockListPixelsPlugin.get3XRefresh()!!.getPixelDefinitions().forEach {
            verify(mockPixel).fire(it.pixelName, it.params)
        }
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

    private fun assignToExperiment() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        testBlockListFeature.tdsNextExperimentTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(Config(treatmentUrl = "treatmentUrl", controlUrl = "controlUrl")),
                assignedCohort = State.Cohort(name = TREATMENT.cohortName, weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
    }

    companion object {
        private const val CURRENT_TIME = 100000L
    }
}
