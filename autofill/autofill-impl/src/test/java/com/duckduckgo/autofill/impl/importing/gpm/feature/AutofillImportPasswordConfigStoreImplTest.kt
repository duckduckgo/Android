package com.duckduckgo.autofill.impl.importing.gpm.feature

import android.annotation.SuppressLint
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStoreImpl.Companion.JAVASCRIPT_CONFIG_DEFAULT
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStoreImpl.Companion.LAUNCH_URL_DEFAULT
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AutofillImportPasswordConfigStoreImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val testee = AutofillImportPasswordConfigStoreImpl(
        autofillFeature = autofillFeature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenFeatureFlagEnabledThenCanImportGooglePasswordsConfigIsEnabled() = runTest {
        configureFeature(true)
        assertTrue(testee.getConfig().canImportFromGooglePasswords)
    }

    @Test
    fun whenFeatureFlagEnabledThenCanImportGooglePasswordsConfigIsDisabled() = runTest {
        configureFeature(false)
        assertFalse(testee.getConfig().canImportFromGooglePasswords)
    }

    @Test
    fun whenLaunchUrlNotSpecifiedInConfigThenDefaultUsed() = runTest {
        configureFeature(config = emptyMap())
        assertEquals(LAUNCH_URL_DEFAULT, testee.getConfig().launchUrlGooglePasswords)
    }

    @Test
    fun whenLaunchUrlSpecifiedInConfigThenOverridesDefault() = runTest {
        configureFeature(config = mapOf("launchUrl" to "https://example.com"))
        assertEquals("https://example.com", testee.getConfig().launchUrlGooglePasswords)
    }

    @Test
    fun whenJavascriptConfigNotSpecifiedInConfigThenDefaultUsed() = runTest {
        configureFeature(config = emptyMap())
        assertEquals(JAVASCRIPT_CONFIG_DEFAULT, testee.getConfig().javascriptConfigGooglePasswords)
    }

    @Test
    fun whenJavascriptConfigSpecifiedInConfigThenOverridesDefault() = runTest {
        configureFeature(config = mapOf("javascriptConfig" to """{"key": "value"}"""))
        assertEquals("""{"key": "value"}""", testee.getConfig().javascriptConfigGooglePasswords)
    }

    @SuppressLint("DenyListedApi")
    private fun configureFeature(enabled: Boolean = true, config: Map<String, String> = emptyMap()) {
        autofillFeature.canImportFromGooglePasswordManager().setRawStoredState(
            State(
                remoteEnableState = enabled,
                config = config,
            ),
        )
    }
}
