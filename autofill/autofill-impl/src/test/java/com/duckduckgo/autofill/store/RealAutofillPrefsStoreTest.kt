package com.duckduckgo.autofill.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autofill.store.feature.AutofillDefaultStateDecider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAutofillPrefsStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val defaultStateDecider: AutofillDefaultStateDecider = mock()

    private val testee = RealAutofillPrefsStore(
        applicationContext = context,
        defaultStateDecider = defaultStateDecider,
    )

    @Test
    fun whenAutofillStateNeverSetManuallyThenDefaultStateDeciderUsed() {
        testee.isEnabled
        verify(defaultStateDecider).defaultState()
    }

    @Test
    fun whenAutofillStateWasManuallySetToEnabledThenDefaultStateDeciderNotUsed() {
        testee.isEnabled = true
        testee.isEnabled
        verify(defaultStateDecider, never()).defaultState()
    }

    @Test
    fun whenAutofillStateWasManuallySetToDisabledThenDefaultStateDeciderNotUsed() {
        testee.isEnabled = false
        testee.isEnabled
        verify(defaultStateDecider, never()).defaultState()
    }

    @Test
    fun whenDeterminedEnabledByDefaultOnceThenNotDecidedAgain() {
        // first call will decide default state should be enabled
        whenever(defaultStateDecider.defaultState()).thenReturn(true)
        assertTrue(testee.isEnabled)
        verify(defaultStateDecider).defaultState()

        // second call should not invoke decider again
        assertTrue(testee.isEnabled)
        verifyNoMoreInteractions(defaultStateDecider)
    }

    @Test
    fun whenDeterminedNotEnabledByDefaultOnceThenWillCallToDeciderAgain() {
        // first call will decide default state should not be enabled
        whenever(defaultStateDecider.defaultState()).thenReturn(false)
        assertFalse(testee.isEnabled)

        // second call should invoke decider again
        assertFalse(testee.isEnabled)
        verify(defaultStateDecider, times(2)).defaultState()
    }
}
