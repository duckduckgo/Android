package com.duckduckgo.app.browser.threatprotection

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ThreatProtectionSettingsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: ThreatProtectionSettingsViewModel
    private val mockSettingsDataStore = mock<SettingsDataStore>()
    private val mockPixel = mock<Pixel>()
    private val fakeAndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val mockMaliciousSiteProtection = mock<MaliciousSiteProtection>()

    @Test
    fun whenEnableMaliciousSiteProtectionIsDisabledThenScamProtectionRCIsDisabled() = runTest {
        testee = ThreatProtectionSettingsViewModel(
            settingsDataStore = mockSettingsDataStore,
            pixel = mockPixel,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            maliciousSiteProtection = mockMaliciousSiteProtection,
        )
        fakeAndroidBrowserConfigFeature.enableMaliciousSiteProtection().setRawStoredState(State(false))
        whenever(mockMaliciousSiteProtection.isFeatureEnabled()).thenReturn(true)

        testee.viewState.test {
            assertFalse(awaitItem()?.scamProtectionRCEnabled!!)
        }
    }

    @Test
    fun whenMaliciousSiteProtectionIsDisabledThenScamProtectionRCIsDisabled() = runTest {
        testee = ThreatProtectionSettingsViewModel(
            settingsDataStore = mockSettingsDataStore,
            pixel = mockPixel,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            maliciousSiteProtection = mockMaliciousSiteProtection,
        )
        fakeAndroidBrowserConfigFeature.enableMaliciousSiteProtection().setRawStoredState(State(true))
        whenever(mockMaliciousSiteProtection.isFeatureEnabled()).thenReturn(false)

        testee.viewState.test {
            assertFalse(awaitItem()?.scamProtectionRCEnabled!!)
        }
    }

    @Test
    fun whenBothMaliciousSiteProtectionAndEnableMaliciousSiteProtectionAreEnabledThenScamProtectionRCIsEnabled() = runTest {
        fakeAndroidBrowserConfigFeature.enableMaliciousSiteProtection().setRawStoredState(State(true))
        whenever(mockMaliciousSiteProtection.isFeatureEnabled()).thenReturn(true)
        testee = ThreatProtectionSettingsViewModel(
            settingsDataStore = mockSettingsDataStore,
            pixel = mockPixel,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            maliciousSiteProtection = mockMaliciousSiteProtection,
        )

        testee.viewState.test {
            assertTrue(awaitItem()?.scamProtectionRCEnabled!!)
        }
    }

    @Test
    fun whenScamProtectionIsUserEnabledThenScamProtectionIsUserEnabled() = runTest {
        fakeAndroidBrowserConfigFeature.enableMaliciousSiteProtection().setRawStoredState(State(true))
        whenever(mockMaliciousSiteProtection.isFeatureEnabled()).thenReturn(true)
        whenever(mockSettingsDataStore.maliciousSiteProtectionEnabled).thenReturn(true)
        whenever(mockSettingsDataStore.maliciousSiteProtectionEnabled).thenReturn(true)
        testee = ThreatProtectionSettingsViewModel(
            settingsDataStore = mockSettingsDataStore,
            pixel = mockPixel,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            maliciousSiteProtection = mockMaliciousSiteProtection,
        )

        testee.viewState.test {
            assertTrue(awaitItem()?.scamProtectionUserEnabled!!)
        }
    }
}
