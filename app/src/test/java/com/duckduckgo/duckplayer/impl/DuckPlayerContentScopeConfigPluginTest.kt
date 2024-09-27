package com.duckduckgo.duckplayer.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckplayer.api.DuckPlayer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckPlayerContentScopeConfigPluginTest {

    private val mockDuckPlayerFeatureRepository: DuckPlayerFeatureRepository = mock()
    private val mockDuckPlayer: DuckPlayer = mock()
    private val testee = DuckPlayerContentScopeConfigPlugin(mockDuckPlayerFeatureRepository, mockDuckPlayer)

    @Test
    fun whenDuckPlayerStateIsDisabledThenStateInJsonIsDisabled() = runTest {
        whenever(mockDuckPlayerFeatureRepository.getDuckPlayerRemoteConfigJson()).thenReturn(
            "{\"state\":\"enabled\",\"features\":{\"enableDuckPlayer\":{\"state\":\"disabled\"}}}",
        )
        whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(DuckPlayer.DuckPlayerState.DISABLED)

        val result = testee.config()
        assertEquals("\"duckPlayer\":{\"state\":\"disabled\",\"features\":{\"enableDuckPlayer\":{\"state\":\"disabled\"}}}", result)
    }

    @Test
    fun whenDuckPlayerStateIsInternalAndEnabledThenStateInJsonIsDisabled() = runTest {
        whenever(mockDuckPlayerFeatureRepository.getDuckPlayerRemoteConfigJson()).thenReturn(
            "{\"state\":\"internal\",\"features\":{\"enableDuckPlayer\":{\"state\":\"internal\"}}}",
        )
        whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(DuckPlayer.DuckPlayerState.ENABLED)

        val result = testee.config()
        assertEquals("\"duckPlayer\":{\"state\":\"enabled\",\"features\":{\"enableDuckPlayer\":{\"state\":\"internal\"}}}", result)
    }

    @Test
    fun whenDuckPlayerStateIsEnabledThenStateInJsonIsEnabled() = runTest {
        whenever(mockDuckPlayerFeatureRepository.getDuckPlayerRemoteConfigJson()).thenReturn(
            "{\"state\":\"enabled\",\"features\":{\"enableDuckPlayer\":{\"state\":\"enabled\"}}}",
        )
        whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(DuckPlayer.DuckPlayerState.ENABLED)

        val result = testee.config()
        assertEquals("\"duckPlayer\":{\"state\":\"enabled\",\"features\":{\"enableDuckPlayer\":{\"state\":\"enabled\"}}}", result)
    }
}
