package com.duckduckgo.app.trackerdetection

import android.annotation.SuppressLint
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.trackerdetection.BlockList.Cohorts.CONTROL
import com.duckduckgo.app.trackerdetection.BlockList.Cohorts.TREATMENT
import com.duckduckgo.app.trackerdetection.BlockList.Companion.CONTROL_URL
import com.duckduckgo.app.trackerdetection.BlockList.Companion.NEXT_URL
import com.duckduckgo.app.trackerdetection.BlockList.Companion.TREATMENT_URL
import com.duckduckgo.app.trackerdetection.api.TDS_BASE_URL
import com.duckduckgo.app.trackerdetection.api.TDS_PATH
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class BlockListInterceptorApiPluginTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testFeature: TestFeature
    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var interceptor: BlockListInterceptorApiPlugin

    @Before
    fun setup() {
        testFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "blockList",
        ).build().create(TestFeature::class.java)

        inventory = RealFeatureTogglesInventory(
            setOf(
                FakeFeatureTogglesInventory(
                    features = listOf(
                        testFeature.tdsNextExperimentTest(),
                        testFeature.tdsNextExperimentAnotherTest(),
                    ),
                ),
            ),
            coroutineRule.testDispatcherProvider,
        )

        interceptor = BlockListInterceptorApiPlugin(inventory)
    }

    @Test
    fun `when multiple experiments enabled, use the first one`() {
        testFeature.tdsNextExperimentTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                config = mapOf(
                    TREATMENT_URL to "treatmentUrl",
                    CONTROL_URL to "controlUrl",
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 0),
                    State.Cohort(name = TREATMENT.cohortName, weight = 1),
                ),
            ),
        )
        testFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                config = mapOf(
                    TREATMENT_URL to "anotherTreatmentUrl",
                    CONTROL_URL to "anotherControlUrl",
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 0),
                    State.Cohort(name = TREATMENT.cohortName, weight = 1),
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl("treatmentUrl"), result.request.url.toString())
    }

    @Test
    fun `when cohort is treatment use treatment URL`() {
        testFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                config = mapOf(
                    TREATMENT_URL to "anotherTreatmentUrl",
                    CONTROL_URL to "anotherControlUrl",
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 0),
                    State.Cohort(name = TREATMENT.cohortName, weight = 1),
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl("anotherTreatmentUrl"), result.request.url.toString())
    }

    @Test
    fun `when cohort is control use control URL`() {
        testFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                config = mapOf(
                    TREATMENT_URL to "anotherTreatmentUrl",
                    CONTROL_URL to "anotherControlUrl",
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 1),
                    State.Cohort(name = TREATMENT.cohortName, weight = 0),
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl("anotherControlUrl"), result.request.url.toString())
    }

    @Test
    fun `when feature is for next URL rollout then use next url`() {
        testFeature.tdsNextExperimentAnotherTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                config = mapOf(
                    NEXT_URL to "nextUrl",
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl("nextUrl"), result.request.url.toString())
    }

    @Test
    fun `when no experiments enabled, use default path`() {
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))
        assertEquals(getUrl(TDS_PATH), result.request.url.toString())
    }

    @Test
    fun `when feature name doesn't match prefix, it is ignored`() {
        testFeature.nonMatchingFeatureName().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                config = mapOf(
                    TREATMENT_URL to "anotherTreatmentUrl",
                    CONTROL_URL to "anotherControlUrl",
                ),
                cohorts = listOf(
                    State.Cohort(name = CONTROL.cohortName, weight = 1),
                    State.Cohort(name = TREATMENT.cohortName, weight = 0),
                ),
            ),
        )
        val url = getUrl(TDS_PATH)
        val result = interceptor.intercept(FakeChain(url))

        assertEquals(getUrl(TDS_PATH), result.request.url.toString())
    }

    private fun getUrl(path: String): String = "$TDS_BASE_URL$path"
}

class FakeFeatureTogglesInventory(private val features: List<Toggle>) : FeatureTogglesInventory {
    override suspend fun getAll(): List<Toggle> {
        return features
    }
}

abstract class TriggerTestScope private constructor()

@ContributesRemoteFeature(
    scope = TriggerTestScope::class,
    featureName = "blockList",
)
interface TestFeature {
    @DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentTest(): Toggle

    @Toggle.DefaultValue(false)
    fun tdsNextExperimentAnotherTest(): Toggle

    @Toggle.DefaultValue(false)
    fun nonMatchingFeatureName(): Toggle
}
