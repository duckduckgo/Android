package com.duckduckgo.malicioussiteprotection.impl

import android.annotation.SuppressLint
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.FDROID
import com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL
import com.duckduckgo.appbuildconfig.api.BuildFlavor.PLAY
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealMaliciousSiteProtectionRCFeatureTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val fakeMaliciousSiteProtectionFeature = FakeFeatureToggleFactory.create(MaliciousSiteProtectionFeature::class.java)
    private val mockAppBuildConfig: AppBuildConfig = mock()

    private val testee = RealMaliciousSiteProtectionRCFeature(
        dispatchers = coroutinesTestRule.testDispatcherProvider,
        maliciousSiteProtectionFeature = fakeMaliciousSiteProtectionFeature,
        appBuildConfig = mockAppBuildConfig,
        isMainProcess = true,
        appCoroutineScope = coroutinesTestRule.testScope,
    )

    @Test
    fun `when RC is enabled but flavor is fdroid then return false`() {
        fakeMaliciousSiteProtectionFeature.self().setRawStoredState(State(enable = true))
        fakeMaliciousSiteProtectionFeature.visibleAndOnByDefault().setRawStoredState(State(enable = true))
        whenever(mockAppBuildConfig.flavor).thenReturn(FDROID)

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.isFeatureEnabled())
    }

    @Test
    fun `when RC is enabled and flavor is internal then return true`() {
        fakeMaliciousSiteProtectionFeature.self().setRawStoredState(State(enable = true))
        fakeMaliciousSiteProtectionFeature.visibleAndOnByDefault().setRawStoredState(State(enable = true))
        whenever(mockAppBuildConfig.flavor).thenReturn(INTERNAL)

        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.isFeatureEnabled())
    }

    @Test
    fun `when RC is enabled and flavor is play then return true`() {
        fakeMaliciousSiteProtectionFeature.self().setRawStoredState(State(enable = true))
        fakeMaliciousSiteProtectionFeature.visibleAndOnByDefault().setRawStoredState(State(enable = true))
        whenever(mockAppBuildConfig.flavor).thenReturn(PLAY)

        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.isFeatureEnabled())
    }

    @Test
    fun `when RC elf is enabled and visibleAndOnyDefault is false and flavor is play then return false`() {
        fakeMaliciousSiteProtectionFeature.self().setRawStoredState(State(enable = true))
        fakeMaliciousSiteProtectionFeature.visibleAndOnByDefault().setRawStoredState(State(enable = false))
        whenever(mockAppBuildConfig.flavor).thenReturn(PLAY)

        testee.onPrivacyConfigDownloaded()

        assertFalse(testee.isFeatureEnabled())
    }
}
