package com.duckduckgo.app.trackerdetection.blocklist

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.blocklist.BlockList.Cohorts.TREATMENT
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import com.duckduckgo.privacy.dashboard.api.PrivacyToggleOrigin.BREAKAGE_FORM
import com.duckduckgo.privacy.dashboard.api.PrivacyToggleOrigin.DASHBOARD
import com.duckduckgo.privacy.dashboard.api.PrivacyToggleOrigin.MENU
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.time.ZoneId
import java.time.ZonedDateTime

@SuppressLint("DenyListedApi")
class BlockListPrivacyTogglePluginTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val moshi = Moshi.Builder().build()

    private data class Config(
        val treatmentUrl: String? = null,
        val controlUrl: String? = null,
        val nextUrl: String? = null,
    )
    private val configAdapter = moshi.adapter(Config::class.java)

    private val pixel: Pixel = mock()
    private lateinit var testBlockListFeature: TestBlockListFeature
    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var blockListPixelsPlugin: BlockListPixelsPlugin
    private lateinit var blockListPrivacyTogglePlugin: BlockListPrivacyTogglePlugin

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
            coroutineRule.testDispatcherProvider,
        )

        blockListPixelsPlugin = BlockListPixelsPlugin(inventory)
        blockListPrivacyTogglePlugin = BlockListPrivacyTogglePlugin(blockListPixelsPlugin, pixel)
    }

    @Test
    fun `when toggle is off and assigned to experiment and origin dashboard then send pixels`() = runTest {
        assignToExperiment()

        blockListPrivacyTogglePlugin.onToggleOff(DASHBOARD)

        blockListPixelsPlugin.getPrivacyToggleUsed()!!.getPixelDefinitions().forEach {
            verify(pixel).fire(it.pixelName, it.params)
        }
    }

    @Test
    fun `when toggle is off and assigned to experiment and origin is not dashboard then do not send pixels`() = runTest {
        assignToExperiment()

        blockListPrivacyTogglePlugin.onToggleOff(MENU)
        verifyNoInteractions(pixel)

        blockListPrivacyTogglePlugin.onToggleOff(BREAKAGE_FORM)
        verifyNoInteractions(pixel)
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
}
