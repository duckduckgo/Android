package com.duckduckgo.subscriptions.impl

import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubscriptionsToggleTargetMatcherPluginTest {

    private val subscriptions: Subscriptions = mock()
    private val matcher = SubscriptionsToggleTargetMatcherPlugin(subscriptions)

    @Test
    fun whenIsEligibleAndNullTargetThenReturnTrue() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)

        assertTrue(matcher.matchesTargetProperty(NULL_TARGET))
    }

    @Test
    fun whenIsNotEligibleAndNullTargetThenReturnTrue() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(false)

        assertTrue(matcher.matchesTargetProperty(NULL_TARGET))
    }

    @Test
    fun whenIsEligibleAndAndTargetMatchesThenTrue() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)

        assertTrue(matcher.matchesTargetProperty(ELIGIBLE_TARGET))
    }

    @Test
    fun whenIsNotEligibleAndAndTargetMatchesThenTrue() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(false)

        assertTrue(matcher.matchesTargetProperty(NOT_ELIGIBLE_TARGET))
    }

    @Test
    fun whenIsEligibleAndAndTargetNotMatchingThenTrue() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)

        assertFalse(matcher.matchesTargetProperty(NOT_ELIGIBLE_TARGET))
    }

    @Test
    fun whenIsNotEligibleAndAndTargetNotMatchingThenTrue() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(false)

        assertFalse(matcher.matchesTargetProperty(ELIGIBLE_TARGET))
    }

    companion object {
        private val NULL_TARGET = Toggle.State.Target(null, null, null, null, null, null, null)
        private val ELIGIBLE_TARGET = NULL_TARGET.copy(isPrivacyProEligible = true)
        private val NOT_ELIGIBLE_TARGET = NULL_TARGET.copy(isPrivacyProEligible = false)
    }
}
