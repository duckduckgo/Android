package com.duckduckgo.contentscopescripts.impl.features.contentscopeexperiments

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.contentscopescripts.impl.features.contentscopeexperiments.ContentScopeExperimentsFeature.Cohorts.CONTROL
import com.duckduckgo.contentscopescripts.impl.features.contentscopeexperiments.ContentScopeExperimentsFeature.Cohorts.TREATMENT
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class RealContentScopeExperimentsTest {

    private val fakeContentScopeExperimentsFeature = FakeFeatureToggleFactory.create(ContentScopeExperimentsFeature::class.java)
    private val mockFeatureTogglesInventory: FeatureTogglesInventory = FakeFeatureTogglesInventory(fakeContentScopeExperimentsFeature)

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val testee = RealContentScopeExperiments(
        contentScopeExperimentsFeature = fakeContentScopeExperimentsFeature,
        featureTogglesInventory = mockFeatureTogglesInventory,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenContentScopeExperimentsFeatureIsDisabledThenGetExperimentsReturnsEmptyList() = runTest {
        fakeContentScopeExperimentsFeature.self().setRawStoredState(Toggle.State(false))
        val experiments = testee.getActiveExperiments()
        assertEquals(listOf<Toggle>(), experiments)
    }

    @Test
    fun whenContentScopeExperimentsFeatureIsEnabledThenGetExperimentsReturnsEnabledExperiments() = runTest {
        fakeContentScopeExperimentsFeature.self().setRawStoredState(Toggle.State(true))
        fakeContentScopeExperimentsFeature.test().setRawStoredState(
            Toggle.State(
                remoteEnableState = true,
                cohorts = listOf(Cohort(name = TREATMENT.cohortName, weight = 1), Cohort(name = CONTROL.cohortName, weight = 1)),
                assignedCohort = Cohort(name = TREATMENT.cohortName, weight = 1),
            ),
        )
        fakeContentScopeExperimentsFeature.bloops().setRawStoredState(
            Toggle.State(
                remoteEnableState = true,
                cohorts = listOf(Cohort(name = TREATMENT.cohortName, weight = 1), Cohort(name = CONTROL.cohortName, weight = 1)),
                assignedCohort = Cohort(name = CONTROL.cohortName, weight = 1),
            ),
        )

        val experimentsJson = testee.getActiveExperiments()
        assertEquals(TREATMENT.cohortName, experimentsJson[0].getCohort()?.name)
        assertEquals(CONTROL.cohortName, experimentsJson[1].getCohort()?.name)
        assertEquals("test", experimentsJson[0].featureName().name)
        assertEquals("bloops", experimentsJson[1].featureName().name)
    }

    @Test
    fun whenContentScopeExperimentsFeatureIsEnabledButExperimentsAreDisabledThenGetExperimentsReturnsEmptyList() = runTest {
        fakeContentScopeExperimentsFeature.self().setRawStoredState(Toggle.State(true))
        fakeContentScopeExperimentsFeature.test().setRawStoredState(
            Toggle.State(
                remoteEnableState = false,
                cohorts = listOf(Cohort(name = TREATMENT.cohortName, weight = 1)),
                assignedCohort = Cohort(name = TREATMENT.cohortName, weight = 1),
            ),
        )
        fakeContentScopeExperimentsFeature.bloops().setRawStoredState(
            Toggle.State(
                remoteEnableState = false,
                cohorts = listOf(Cohort(name = CONTROL.cohortName, weight = 1)),
                assignedCohort = Cohort(name = CONTROL.cohortName, weight = 1),
            ),
        )

        val experiments = testee.getActiveExperiments()
        assertEquals(listOf<Toggle>(), experiments)
    }

    @Test
    fun whenContentScopeExperimentsFeatureIsEnabledAndMixedExperimentStatesThenGetExperimentsReturnsOnlyEnabledExperiments() = runTest {
        fakeContentScopeExperimentsFeature.self().setRawStoredState(Toggle.State(true))
        fakeContentScopeExperimentsFeature.test().setRawStoredState(
            Toggle.State(
                remoteEnableState = true,
                cohorts = listOf(Cohort(name = TREATMENT.cohortName, weight = 1)),
                assignedCohort = Cohort(name = TREATMENT.cohortName, weight = 1),
            ),
        )
        fakeContentScopeExperimentsFeature.bloops().setRawStoredState(
            Toggle.State(
                remoteEnableState = false,
                cohorts = listOf(Cohort(name = CONTROL.cohortName, weight = 1)),
                assignedCohort = Cohort(name = CONTROL.cohortName, weight = 1),
            ),
        )

        val experiments = testee.getActiveExperiments()
        assertEquals(1, experiments.size)
        assertEquals(TREATMENT.cohortName, experiments[0].getCohort()?.name)
        assertEquals("test", experiments[0].featureName().name)
    }
}

class FakeFeatureTogglesInventory(private val feature: ContentScopeExperimentsFeature) : FeatureTogglesInventory {
    override suspend fun getAll(): List<Toggle> {
        TODO("Not yet implemented")
    }

    override suspend fun getAllTogglesForParent(name: String): List<Toggle> {
        return listOf(feature.test(), feature.bloops())
    }
}
