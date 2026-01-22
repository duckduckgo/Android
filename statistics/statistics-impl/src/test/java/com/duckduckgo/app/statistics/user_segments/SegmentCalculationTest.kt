package com.duckduckgo.app.statistics.user_segments

import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.statistics.user_segments.SegmentCalculation.ActivityType.APP_USE
import com.duckduckgo.app.statistics.user_segments.SegmentCalculation.ActivityType.DUCKAI
import com.duckduckgo.app.statistics.user_segments.SegmentCalculation.ActivityType.SEARCH
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities.loadText
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class SegmentCalculationTest(private val input: TestInput) {
    data class Client(
        val atb: String,
        val activity_type: String,
        val usage: List<String>,
    )

    data class Result(
        val set_atb: String,
        val pixel_uri: String?,
    )

    data class TestInput(val client: Client, val results: List<Result>)

    // private lateinit var input: List<TestInput>
    companion object {
        private val moshi = Moshi.Builder().build()
        private val jsonAdapter: JsonAdapter<List<TestInput>> = moshi.adapter(Types.newParameterizedType(List::class.java, TestInput::class.java))

        @JvmStatic
        @Parameterized.Parameters(name = "User Segment Test case: {index} - {0}")
        fun testData(): List<TestInput> {
            return jsonAdapter.fromJson(loadText(javaClass.classLoader!!, "json/mobile_segments_test_cases.json"))!!
        }
    }

    private lateinit var usageHistory: UsageHistory
    private lateinit var atbStore: StatisticsDataStore
    private val appBuildConfig = mock<AppBuildConfig>()
    private val mockPixel: Pixel = mock()
    private val crashLogger: CrashLogger = org.mockito.kotlin.mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var segmentCalculation: SegmentCalculation
    private lateinit var userSegmentsPixelSender: UserSegmentsPixelSender

    @Before
    fun setup() = runTest {
        atbStore = FakeStatisticsDataStore()
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)

        usageHistory = SegmentStoreModule().provideSegmentStore(
            FakeSharedPreferencesProvider(),
            coroutineTestRule.testDispatcherProvider,
        )

        segmentCalculation = RealSegmentCalculation(
            coroutineTestRule.testDispatcherProvider,
            atbStore,
            appBuildConfig,
        )
        userSegmentsPixelSender = UserSegmentsPixelSender(
            usageHistory,
            segmentCalculation,
            mockPixel,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
            crashLogger,
        )
    }

    @Test
    fun `test user segment computation`() = runTest {
        // prepping test
        atbStore.atb = Atb(input.client.atb.removeSuffix("ru"))
        if (input.client.atb.contains("ru")) {
            whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        }

        input.client.usage.forEachIndexed { index, usage ->
            input.results[index].asParameterMap()?.let { expected ->
                val activityType = when (expected["activity_type"]) {
                    "search" -> SEARCH
                    "duckai" -> DUCKAI
                    else -> APP_USE
                }
                val usageHistoryList = when (activityType) {
                    SEARCH -> {
                        usageHistory.addSearchUsage(usage)
                        usageHistory.getSearchUsageHistory()
                    }
                    APP_USE -> {
                        usageHistory.addAppUsage(usage)
                        usageHistory.getAppUsageHistory()
                    }
                    DUCKAI -> {
                        usageHistory.addDuckAiUsage(usage)
                        usageHistory.getDuckAiHistory()
                    }
                }
                val actual = segmentCalculation.computeUserSegmentForActivityType(activityType, usageHistoryList)
                assertEquals(expected, actual.toPixelParams())
            }
        }
    }

    @Test
    fun `test user segment pixel sender`() = runTest {
        // prepping test
        atbStore.atb = Atb(input.client.atb.removeSuffix("ru"))
        if (input.client.atb.contains("ru")) {
            whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        }
        var oldAtb: String = atbStore.atb!!.version
        input.client.usage.forEachIndexed { index, usage ->
            val expected = input.results[index].asParameterMap()
            val activityType = when (input.client.activity_type) {
                "search" -> SEARCH
                "duckai" -> DUCKAI
                else -> APP_USE
            }

            val actual = userSegmentsPixelSender.handleAtbRefresh(activityType, oldAtb, usage)
            oldAtb = usage

            assertEquals(expected.orEmpty(), actual)
        }
    }

    private fun Result.asParameterMap(): Map<String, String>? {
        val url = pixel_uri?.toHttpUrl() ?: return null
        return url.queryParameterNames
            .filterNot { it == "appVersion" || it == "test" }
            .associateWith { param ->
                url.queryParameter(param) ?: ""
            }
    }
}

private class FakeStatisticsDataStore : StatisticsDataStore {
    override val hasInstallationStatistics: Boolean = false

    override var atb: Atb? = null

    override var appRetentionAtb: String? = ""

    override var searchRetentionAtb: String? = ""

    override var duckaiRetentionAtb: String? = ""

    override var variant: String? = ""

    override var referrerVariant: String? = ""
    override fun saveAtb(atb: Atb) {}
    override fun clearAtb() {}
}
