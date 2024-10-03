package com.duckduckgo.autofill.store.feature

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealAutofillDefaultStateDeciderTest {

    private val userBrowserProperties: UserBrowserProperties = mock()
    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val internalTestUserChecker: InternalTestUserChecker = mock()
    private val testee = RealAutofillDefaultStateDecider(
        userBrowserProperties = userBrowserProperties,
        autofillFeature = autofillFeature,
        internalTestUserChecker = internalTestUserChecker,
    )

    @Test
    fun whenRemoteFeatureDisabledThenNumberOfDaysInstalledIsIrrelevant() {
        configureRemoteFeatureEnabled(onByDefaultNewUsers = false)

        configureDaysInstalled(0)
        assertFalse(testee.defaultState())

        configureDaysInstalled(1000)
        assertFalse(testee.defaultState())
    }

    @Test
    fun whenNumberOfDaysInstalledIsNotZeroThenFeatureFlagIsIrrelevant() {
        configureDaysInstalled(10)

        configureRemoteFeatureEnabled(onByDefaultNewUsers = false)
        assertFalse(testee.defaultState())

        configureRemoteFeatureEnabled(onByDefaultNewUsers = true)
        assertFalse(testee.defaultState())
    }

    @Test
    fun whenNumberOfDaysInstalledIsNotZeroThenReturnBasedOnExistingUsersRemoteFlag() {
        configureDaysInstalled(10)

        configureRemoteFeatureEnabled(onByDefaultNewUsers = false, onByDefaultExistingUsers = false)
        assertFalse(testee.defaultState())

        configureRemoteFeatureEnabled(onByDefaultNewUsers = true, onByDefaultExistingUsers = true)
        assertTrue(testee.defaultState())
    }

    @Test
    fun whenInternalTesterThenAlwaysEnabledByDefault() {
        configureDaysInstalled(100)
        configureRemoteFeatureEnabled(onByDefaultNewUsers = false, onByDefaultExistingUsers = false)
        configureAsInternalTester()
        assertTrue(testee.defaultState())
    }

    @Test
    fun whenInstalledSameDayAndFeatureFlagEnabledThenEnabledByDefault() {
        configureDaysInstalled(0)
        configureRemoteFeatureEnabled(onByDefaultNewUsers = true)
        assertTrue(testee.defaultState())
    }

    private fun configureAsInternalTester() {
        whenever(internalTestUserChecker.isInternalTestUser).thenReturn(true)
    }

    private fun configureRemoteFeatureEnabled(onByDefaultNewUsers: Boolean, onByDefaultExistingUsers: Boolean = false) {
        autofillFeature.onByDefault().setRawStoredState(State(enable = onByDefaultNewUsers))
        autofillFeature.onForExistingUsers().setRawStoredState(State(enable = onByDefaultExistingUsers))
    }

    private fun configureDaysInstalled(daysInstalled: Long) {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(daysInstalled)
    }
}
