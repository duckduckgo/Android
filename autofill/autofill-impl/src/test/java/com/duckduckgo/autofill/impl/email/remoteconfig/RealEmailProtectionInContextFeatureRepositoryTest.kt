package com.duckduckgo.autofill.impl.email.remoteconfig

import android.annotation.SuppressLint
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi") // setRawStoredState
class RealEmailProtectionInContextFeatureRepositoryTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(EmailProtectionInContextSignupFeature::class.java)

    @Before
    fun setup() {
        feature.self().setRawStoredState(Toggle.State(exceptions = exceptions))
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadIntoMemory() = runTest {
        val repository = RealEmailProtectionInContextFeatureRepository(
            feature,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            true,
        )

        assertEquals(exceptions.map { it.domain }, repository.exceptions)
    }

    @Test
    fun whenRemoteConfigUpdateThenExceptionsUpdated() = runTest {
        val repository = RealEmailProtectionInContextFeatureRepository(
            feature,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            true,
        )

        assertEquals(exceptions.map { it.domain }, repository.exceptions)
        feature.self().setRawStoredState(Toggle.State(exceptions = emptyList()))
        repository.onPrivacyConfigDownloaded()
        assertEquals(emptyList<FeatureException>(), repository.exceptions)
    }

    companion object {
        val exceptions = listOf(FeatureException("example.com", "reason"))
    }
}
