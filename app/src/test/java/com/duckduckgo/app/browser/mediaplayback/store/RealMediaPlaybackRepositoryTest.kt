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
        mediaPlaybackFeature.self().setRawStoredState(Toggle.State(exceptions = exceptions))
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
    }

    @Test
    fun whenRemoteConfigUpdateThenExceptionsUpdated() = runTest {
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

    companion object {
        val exceptions = listOf(FeatureException("example.com", "reason"))
    }
}
