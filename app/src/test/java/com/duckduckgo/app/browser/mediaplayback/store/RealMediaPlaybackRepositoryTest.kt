package com.duckduckgo.app.browser.mediaplayback.store

import com.duckduckgo.app.browser.mediaplayback.MediaPlaybackFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RealMediaPlaybackRepositoryTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mediaPlaybackFeature = FakeFeatureToggleFactory.create(MediaPlaybackFeature::class.java)

    @Before
    fun before() {
        mediaPlaybackFeature.self().setRawStoredState(
            Toggle.State(
                exceptions = exceptions,
                settings = exemptedDomainsSettingsJson,
            ),
        )
    }

    @Test
    fun whenRepositoryIsCreatedThenValuesLoadedIntoMemory() {
        val repository = RealMediaPlaybackRepository(
            mediaPlaybackFeature,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            isMainProcess = true,
        )

        assertEquals(exceptions, repository.exceptions)
        assertEquals(exemptedDomains, repository.exemptedDomains)
    }

    @Test
    fun whenRemoteConfigUpdatedThenExceptionsUpdated() = runTest {
        val repository = RealMediaPlaybackRepository(
            mediaPlaybackFeature,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            isMainProcess = true,
        )

        assertEquals(exceptions, repository.exceptions)
        mediaPlaybackFeature.self().setRawStoredState(Toggle.State(exceptions = emptyList()))
        repository.onPrivacyConfigDownloaded()
        assertEquals(emptyList<FeatureException>(), repository.exceptions)
    }

    @Test
    fun whenRemoteConfigUpdatedThenExemptedDomainsUpdated() = runTest {
        val repository = RealMediaPlaybackRepository(
            mediaPlaybackFeature,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            isMainProcess = true,
        )

        assertEquals(exemptedDomains, repository.exemptedDomains)

        val updatedSettingsJson = """
            {
                "exemptedDomains": [
                    {"domain": "domain.com", "reason": "reason"},
                    {"domain": "another.com", "reason": "reason"}
                ]
            }
        """.trimIndent()

        mediaPlaybackFeature.self().setRawStoredState(
            Toggle.State(
                exceptions = exceptions,
                settings = updatedSettingsJson,
            ),
        )
        repository.onPrivacyConfigDownloaded()

        assertEquals(listOf("domain.com", "another.com"), repository.exemptedDomains)
    }

    @Test
    fun whenSettingsIsNullThenExemptedDomainsIsEmpty() = runTest {
        mediaPlaybackFeature.self().setRawStoredState(
            Toggle.State(
                exceptions = exceptions,
                settings = null,
            ),
        )

        val repository = RealMediaPlaybackRepository(
            mediaPlaybackFeature,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            isMainProcess = true,
        )

        assertEquals(emptyList<String>(), repository.exemptedDomains)
    }

    @Test
    fun whenSettingsHasEmptyExemptedDomainsThenExemptedDomainsIsEmpty() = runTest {
        mediaPlaybackFeature.self().setRawStoredState(
            Toggle.State(
                exceptions = exceptions,
                settings = emptyExemptedDomainsSettingsJson,
            ),
        )

        val repository = RealMediaPlaybackRepository(
            mediaPlaybackFeature,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            isMainProcess = true,
        )

        assertEquals(emptyList<String>(), repository.exemptedDomains)
    }

    @Test
    fun whenSettingsIsInvalidJsonThenExemptedDomainsIsEmpty() = runTest {
        mediaPlaybackFeature.self().setRawStoredState(
            Toggle.State(
                exceptions = exceptions,
                settings = "invalid json",
            ),
        )

        val repository = RealMediaPlaybackRepository(
            mediaPlaybackFeature,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            isMainProcess = true,
        )

        assertEquals(emptyList<String>(), repository.exemptedDomains)
    }

    companion object {
        val exceptions = listOf(FeatureException("example.com", "reason"))
        val exemptedDomains = listOf("foo.com", "example.com")
        val exemptedDomainsSettingsJson = """
            {
                "exemptedDomains": [
                    {"domain": "foo.com", "reason": "reason"},
                    {"domain": "example.com", "reason": "reason"}
                ]
            }
        """.trimIndent()
        val emptyExemptedDomainsSettingsJson = """
            {
                "exemptedDomains": []
            }
        """.trimIndent()
    }
}
