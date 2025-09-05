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
        configureFeature(config = Config(urlMappings = listOf(UrlMapping("key", "https://example.com"))))
        assertEquals(LAUNCH_URL_DEFAULT, testee.getConfig().launchUrlGooglePasswords)
    }

    @Test
    fun whenLaunchUrlSpecifiedInConfigThenOverridesDefault() = runTest {
        configureFeature(config = Config(launchUrl = "https://example.com", urlMappings = listOf(UrlMapping("key", "https://example.com"))))
        assertEquals("https://example.com", testee.getConfig().launchUrlGooglePasswords)
    }

    @Test
    fun whenJavascriptConfigNotSpecifiedInConfigThenDefaultUsed() = runTest {
        configureFeature(config = Config(urlMappings = listOf(UrlMapping("key", "https://example.com"))))
        assertEquals(JAVASCRIPT_CONFIG_DEFAULT, testee.getConfig().javascriptConfigGooglePasswords)
    }

    @Test
    fun whenJavascriptConfigSpecifiedInConfigThenOverridesDefault() = runTest {
        configureFeature(
            config = Config(
                javascriptConfig = JavaScriptConfig(key = "value", domains = listOf("foo, bar")),
                urlMappings = listOf(UrlMapping("key", "https://example.com")),
            ),
        )
        assertEquals("""{"domains":["foo, bar"],"key":"value"}""", testee.getConfig().javascriptConfigGooglePasswords)
    }

    @Test
    fun whenUrlMappingsSpecifiedInConfigOverridesDefault() = runTest {
        configureFeature(config = Config(urlMappings = listOf(UrlMapping("key", "https://example.com"))))
        testee.getConfig().urlMappings.apply {
            assertEquals(1, size)
            assertEquals("key", get(0).key)
            assertEquals("https://example.com", get(0).url)
        }
    }

    @Test
    fun whenUrlMappingsNotSpecifiedInConfigThenDefaultsUsed() = runTest {
        configureFeature(config = Config(urlMappings = null))
        assertEquals(6, testee.getConfig().urlMappings.size)
    }

    @Test
    fun whenUrlMappingsNotSpecifiedInConfigThenCorrectOrderOfDefaultsReturned() = runTest {
        configureFeature(config = Config(urlMappings = null))
        testee.getConfig().urlMappings.apply {
            assertEquals("webflow-signin-rejected", get(0).key)
            assertEquals("webflow-passphrase-encryption", get(1).key)
            assertEquals("webflow-pre-login", get(2).key)
            assertEquals("webflow-export", get(3).key)
            assertEquals("webflow-authenticate", get(4).key)
            assertEquals("webflow-post-login-landing", get(5).key)
        }
    }

    @SuppressLint("DenyListedApi")
    private fun configureFeature(enabled: Boolean = true, config: Config = Config(urlMappings = listOf(UrlMapping("key", "https://example.com")))) {
        autofillFeature.canImportFromGooglePasswordManager().setRawStoredState(
            State(
                remoteEnableState = enabled,
                settings = adapter.toJson(config),
            ),
        )
    }
    private data class Config(
        val launchUrl: String? = null,
        val canInjectJavascript: Boolean = true,
        val javascriptConfig: JavaScriptConfig? = null,
        val urlMappings: List<UrlMapping>?,
    )

    private data class JavaScriptConfig(
        val key: String,
        val domains: List<String>,
    )
}
