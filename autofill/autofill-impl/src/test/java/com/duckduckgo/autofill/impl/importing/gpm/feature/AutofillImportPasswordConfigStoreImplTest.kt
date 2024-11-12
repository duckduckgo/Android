package com.duckduckgo.autofill.impl.importing.gpm.feature

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStoreImpl.Companion.JAVASCRIPT_CONFIG_DEFAULT
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStoreImpl.Companion.LAUNCH_URL_DEFAULT
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.json.JSONObjectAdapter
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutofillImportPasswordConfigStoreImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val adapter: JsonAdapter<Config> = moshi.adapter(Config::class.java)

    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val testee = AutofillImportPasswordConfigStoreImpl(
        autofillFeature = autofillFeature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        moshi = moshi,
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
        configureFeature(config = Config())
        assertEquals(LAUNCH_URL_DEFAULT, testee.getConfig().launchUrlGooglePasswords)
    }

    @Test
    fun whenLaunchUrlSpecifiedInConfigThenOverridesDefault() = runTest {
        configureFeature(config = Config(launchUrl = "https://example.com"))
        assertEquals("https://example.com", testee.getConfig().launchUrlGooglePasswords)
    }

    @Test
    fun whenJavascriptConfigNotSpecifiedInConfigThenDefaultUsed() = runTest {
        configureFeature(config = Config())
        assertEquals(JAVASCRIPT_CONFIG_DEFAULT, testee.getConfig().javascriptConfigGooglePasswords)
    }

    @Test
    fun whenJavascriptConfigSpecifiedInConfigThenOverridesDefault() = runTest {
        configureFeature(config = Config(javascriptConfig = JavaScriptConfig(key = "value", domains = listOf("foo, bar"))))
        assertEquals("""{"domains":["foo, bar"],"key":"value"}""", testee.getConfig().javascriptConfigGooglePasswords)
    }

    @SuppressLint("DenyListedApi")
    private fun configureFeature(enabled: Boolean = true, config: Config = Config()) {
        autofillFeature.canImportFromGooglePasswordManager().setRawStoredState(
            State(
                remoteEnableState = enabled,
                config = adapter.toJson(config),
            ),
        )
    }

    private data class Config(
        val launchUrl: String? = null,
        val javascriptConfig: JavaScriptConfig? = null,
    )
    private data class JavaScriptConfig(
        val key: String,
        val domains: List<String>,
    )
}
