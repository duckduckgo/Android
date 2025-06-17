package com.duckduckgo.contentscopescripts.impl.features.contentscopeexperiments

import android.annotation.SuppressLint
import com.duckduckgo.contentscopescripts.impl.features.contentscopeexperiments.ContentScopeExperimentsFeature.Cohorts.CONTROL
import com.duckduckgo.contentscopescripts.impl.features.contentscopeexperiments.ContentScopeExperimentsFeature.Cohorts.TREATMENT
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import org.junit.Test

@SuppressLint("DenyListedApi")
class RealContentScopeExperimentsTest {

    private val fakeContentScopeExperimentsFeature = FakeFeatureToggleFactory.create(ContentScopeExperimentsFeature::class.java)
    private val mockFeatureTogglesInventory: FeatureTogglesInventory = FakeFeatureTogglesInventory(fakeContentScopeExperimentsFeature)

    private val testee = RealContentScopeExperiments(
        contentScopeExperimentsFeature = fakeContentScopeExperimentsFeature,
        featureTogglesInventory = mockFeatureTogglesInventory,
    )

    @Test
    fun whenContentScopeExperimentsFeatureIsDisabledThenGetExperimentsJsonReturnsEmptyList() {
        fakeContentScopeExperimentsFeature.self().setRawStoredState(Toggle.State(false))
        val experimentsJson = testee.getExperimentsJson()
        assert(experimentsJson == "[]")
    }

    @Test
    fun whenContentScopeExperimentsFeatureIsEnabledThenGetExperimentsJsonReturnsEnabledExperiments() {
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

        val experimentsJson = testee.getExperimentsJson()
        assert(
            experimentsJson == "[{\"cohort\":\"treatment\",\"feature\":\"fakeFeature\",\"subfeature\":\"test\"}," +
                "{\"cohort\":\"control\",\"feature\":\"fakeFeature\",\"subfeature\":\"bloops\"}]",
        )
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
