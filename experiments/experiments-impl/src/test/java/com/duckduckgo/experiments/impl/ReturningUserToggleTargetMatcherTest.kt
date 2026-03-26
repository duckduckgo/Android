package com.duckduckgo.experiments.impl

import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ReturningUserToggleTargetMatcherTest {
    private val variantManager: VariantManager = mock()
    private val matcher = ReturningUserToggleTargetMatcher(variantManager)

    @Test
    fun whenReturningUserAndNullTargetThenMatchesTargetReturnsTrue() {
        whenever(variantManager.getVariantKey()).thenReturn("ru")

        assertTrue(matcher.matchesTargetProperty(NULL_TARGET))
    }

    @Test
    fun whenNotReturningUserAndNullTargetThenMatchesTargetReturnsTrue() {
        whenever(variantManager.getVariantKey()).thenReturn("foo")

        assertTrue(matcher.matchesTargetProperty(NULL_TARGET))
    }

    @Test
    fun whenReturningUserAndTargetMatchesThenReturnTrue() {
        whenever(variantManager.getVariantKey()).thenReturn("ru")

        assertTrue(matcher.matchesTargetProperty(RU_TARGET))
    }

    @Test
    fun whenNoReturningUserAndTargetMatchesThenReturnTrue() {
        whenever(variantManager.getVariantKey()).thenReturn("foo")

        assertTrue(matcher.matchesTargetProperty(NOT_RU_TARGET))
    }

    @Test
    fun whenReturningUserAndTargetNotMatchingThenReturnFalse() {
        whenever(variantManager.getVariantKey()).thenReturn("ru")

        assertFalse(matcher.matchesTargetProperty(NOT_RU_TARGET))
    }

    @Test
    fun whenNoReturningUserAndTargetNotMatchingThenReturnTrue() {
        whenever(variantManager.getVariantKey()).thenReturn("foo")

        assertFalse(matcher.matchesTargetProperty(RU_TARGET))
    }

    companion object {
        private val NULL_TARGET = Toggle.State.Target(null, null, null, null, null, null, null)
        private val RU_TARGET = NULL_TARGET.copy(isReturningUser = true)
        private val NOT_RU_TARGET = NULL_TARGET.copy(isReturningUser = false)
    }
}
