package com.duckduckgo.app.survey.rmf

import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles.Cohorts
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TemporaryDefaultSurveyParametersPluginTest {

    private val mockSenseOfProtectionToggles: SenseOfProtectionToggles = mock()

    @Test
    fun givenUserAssignedToModifiedControlThenCohortParamEvaluatesToModifiedControl() = runTest {
        val modifiedControlCohort = Cohort(Cohorts.MODIFIED_CONTROL.cohortName, 1)
        val mockToggle: Toggle = mock {
            on { it.isEnabled() } doReturn true
            onBlocking { it.getCohort() } doReturn modifiedControlCohort
        }
        whenever(mockSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25()).thenReturn(mockToggle)

        val plugin = SenseOfProtectionCohortSurveyParameterPlugin(mockSenseOfProtectionToggles)

        assertEquals("modifiedControl", plugin.evaluate("senseProtectionCohort"))
    }

    @Test
    fun givenUserAssignedToVariant1ThenCohortParamEvaluatesToVariant1() = runTest {
        val modifiedControlCohort = Cohort(Cohorts.VARIANT_1.cohortName, 1)
        val mockToggle: Toggle = mock {
            on { it.isEnabled() } doReturn true
            onBlocking { it.getCohort() } doReturn modifiedControlCohort
        }
        whenever(mockSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25()).thenReturn(mockToggle)

        val plugin = SenseOfProtectionCohortSurveyParameterPlugin(mockSenseOfProtectionToggles)

        assertEquals("variant1", plugin.evaluate("senseProtectionCohort"))
    }

    @Test
    fun givenUserAssignedToVariant2ThenCohortParamEvaluatesToVariant2() = runTest {
        val modifiedControlCohort = Cohort(Cohorts.VARIANT_2.cohortName, 2)
        val mockToggle: Toggle = mock {
            on { it.isEnabled() } doReturn true
            onBlocking { it.getCohort() } doReturn modifiedControlCohort
        }
        whenever(mockSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25()).thenReturn(mockToggle)

        val plugin = SenseOfProtectionCohortSurveyParameterPlugin(mockSenseOfProtectionToggles)

        assertEquals("variant2", plugin.evaluate("senseProtectionCohort"))
    }

    @Test
    fun givenNotAssignedOnSenseOfProtectionExperimentThenCohortParamEvaluatesToEmptyString() = runTest {
        val mockToggle: Toggle = mock {
            on { it.isEnabled() } doReturn false
            onBlocking { it.getCohort() } doReturn null
        }
        whenever(mockSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25()).thenReturn(mockToggle)

        val plugin = SenseOfProtectionCohortSurveyParameterPlugin(mockSenseOfProtectionToggles)

        assertEquals("", plugin.evaluate("senseProtectionCohort"))
    }
}
