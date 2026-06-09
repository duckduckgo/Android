package com.duckduckgo.malicioussiteprotection.impl.remoteconfig

import android.annotation.SuppressLint
import com.duckduckgo.app.browser.Domain
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi") // setRawStoredState
class RealMaliciousSiteProtectionRCRepositoryTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(MaliciousSiteProtectionFeature::class.java)

    @Before
    fun setup() {
        feature.self().setRawStoredState(Toggle.State(exceptions = exceptions))
    }

    @Test
    fun whenHostMatchesExceptionDomainExactlyThenIsExempted() = runTest {
        val repository = createRepository()

        assertTrue(repository.isExempted(Domain("example.com")))
    }

    @Test
    fun whenHostIsSubdomainOfExceptionDomainThenIsExempted() = runTest {
        val repository = createRepository()

        assertTrue(repository.isExempted(Domain("sub.example.com")))
        assertTrue(repository.isExempted(Domain("a.b.example.com")))
    }

    @Test
    fun whenHostIsUnrelatedDomainThenIsNotExempted() = runTest {
        val repository = createRepository()

        assertFalse(repository.isExempted(Domain("other.com")))
    }

    @Test
    fun whenHostSharesSuffixButIsNotSubdomainThenIsNotExempted() = runTest {
        val repository = createRepository()

        assertFalse(repository.isExempted(Domain("notexample.com")))
        assertFalse(repository.isExempted(Domain("fakeexample.com")))
    }

    @Test
    fun whenExceptionsListIsEmptyThenIsNotExempted() = runTest {
        feature.self().setRawStoredState(Toggle.State(exceptions = emptyList()))
        val repository = createRepository()

        assertFalse(repository.isExempted(Domain("example.com")))
    }

    @Test
    fun whenAnyExceptionMatchesThenIsExempted() = runTest {
        feature.self().setRawStoredState(
            Toggle.State(
                exceptions = listOf(
                    FeatureException("first.com", "reason"),
                    FeatureException("example.com", "reason"),
                    FeatureException("third.com", "reason"),
                ),
            ),
        )
        val repository = createRepository()

        assertTrue(repository.isExempted(Domain("sub.example.com")))
    }

    @Test
    fun whenRemoteConfigUpdateThenIsExemptedReflectsNewExceptions() = runTest {
        val repository = createRepository()

        assertTrue(repository.isExempted(Domain("example.com")))

        feature.self().setRawStoredState(
            Toggle.State(exceptions = listOf(FeatureException("other.com", "reason"))),
        )
        repository.onPrivacyConfigDownloaded()

        assertFalse(repository.isExempted(Domain("example.com")))
        assertTrue(repository.isExempted(Domain("other.com")))
    }

    private fun createRepository() = RealMaliciousSiteProtectionRCRepository(
        feature,
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
        true,
    )

    companion object {
        val exceptions = listOf(FeatureException("example.com", "reason"))
    }
}
