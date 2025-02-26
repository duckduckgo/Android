package com.duckduckgo.autofill.impl.username

import android.annotation.SuppressLint
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class RealAutofillUsernameComparerTest {

    private val feature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val testee = RealAutofillUsernameComparer(
        autofillFeature = feature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenBothUsernamesAreNullThenAreEqual() = runTest {
        assertTrue(testee.isEqual(null, null))
    }

    @Test
    fun whenBothUsernamesAreEmptyThenAreEqual() = runTest {
        assertTrue(testee.isEqual("", ""))
    }

    @Test
    fun whenFirstUsernameIsNullAndOtherIsNotThenAreNotEqual() = runTest {
        assertFalse(testee.isEqual(null, "user"))
    }

    @Test
    fun whenFirstUsernameIsEmptyAndOtherIsNotThenAreNotEqual() = runTest {
        assertFalse(testee.isEqual("", "user"))
    }

    @Test
    fun whenSecondUsernameIsNullAndOtherIsNotThenAreNotEqual() = runTest {
        assertFalse(testee.isEqual("user", null))
    }

    @Test
    fun whenSecondUsernameIsEmptyAndOtherIsNotThenAreNotEqual() = runTest {
        assertFalse(testee.isEqual("user", ""))
    }

    @Test
    fun whenOneUsernameIsEmptyAndOtherIsNotThenAreNotEqual() = runTest {
        assertFalse(testee.isEqual("", "user2"))
    }

    @Test
    fun whenUsernamesAreTotallyDifferentThenAreNotEqual() = runTest {
        assertFalse(testee.isEqual("user1", "user2"))
    }

    @Test
    fun whenUsernamesAreIdenticalIncludingCaseThenAreEqual() = runTest {
        assertTrue(testee.isEqual("user", "user"))
    }

    @Test
    fun whenUsernamesAreIdenticalApartFromCaseThenAreEqual() = runTest {
        assertTrue(testee.isEqual("user", "USER"))

        // check when feature flag disabled
        feature.ignoreCaseOnUsernameComparisons().setRawStoredState(State(enable = false))
        assertFalse(testee.isEqual("user", "USER"))
    }
}
