package com.duckduckgo.app.browser.refreshpixels

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
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
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import com.squareup.moshi.Moshi
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

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

    private val mockPixel: Pixel = mock()
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

        testee = DuckDuckGoRefreshPixelSender(
            pixel = mockPixel,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            blockListPixelsPlugin = BlockListPixelsPlugin(inventory),
        )
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
    fun whenRefreshedTwiceOrThriceAndAssignedToExperimentThen2XRefreshPixelsFired() = kotlinx.coroutines.test.runTest {
        assignToExperiment()
        testee.sendBreakageRefreshPixels(setOf(2, 3))

        blockListPixelsPlugin.get2XRefresh()?.getPixelDefinitions()?.forEach {
            verify(mockPixel).fire(it.pixelName, it.params)
        }

        blockListPixelsPlugin.get3XRefresh()?.getPixelDefinitions()?.forEach {
            verify(mockPixel).fire(it.pixelName, it.params)
        }
    }

    private fun assignToExperiment() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        testBlockListFeature.tdsNextExperimentTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(Config(treatmentUrl = "treatmentUrl", controlUrl = "controlUrl")),
                assignedCohort = Cohort(name = TREATMENT.cohortName, weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
    }
}
