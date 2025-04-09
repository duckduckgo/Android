package com.duckduckgo.autofill.impl.configuration

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.configuration.AutofillSiteSpecificFixesSettingsImpl.Companion.JAVASCRIPT_CONFIG_DEFAULT
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.json.JSONObjectAdapter
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class AutofillSiteSpecificFixesSettingsImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val adapter = moshi.adapter(JSONObject::class.java)

    private val testee = AutofillSiteSpecificFixesSettingsImpl(
        autofillFeature = feature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        moshi = moshi,
    )

    @Test
    fun whenFeatureDisabledAndSettingsEmptyThenFeatureDisabledWithEmptySettings() = runTest {
        feature.siteSpecificFixes().setRawStoredState(State(enable = false, settings = JAVASCRIPT_CONFIG_DEFAULT))
        val settings = testee.getConfig()
        assertFalse(settings.canApplySiteSpecificFixes)
        assertIsEmptyJson(settings.javascriptConfigSiteSpecificFixes)
    }

    @Test
    fun whenFeatureEnabledAndSettingsEmptyThenFeatureEnabledWithEmptySettings() = runTest {
        feature.siteSpecificFixes().setRawStoredState(State(enable = true, settings = JAVASCRIPT_CONFIG_DEFAULT))
        val settings = testee.getConfig()
        assertTrue(settings.canApplySiteSpecificFixes)
        assertIsEmptyJson(settings.javascriptConfigSiteSpecificFixes)
    }

    @Test
    fun whenFeatureDisabledAndSettingsPopulatedThenFeatureDisabledWithEmptySettings() = runTest {
        feature.siteSpecificFixes().setRawStoredState(State(enable = false, settings = adapter.toJson(POPULATED_CONFIG)))
        val settings = testee.getConfig()
        assertFalse(settings.canApplySiteSpecificFixes)
        assertIsEmptyJson(settings.javascriptConfigSiteSpecificFixes)
    }

    @Test
    fun whenFeatureEnabledAndSettingsPopulatedThenFeatureEnabledWithPopulatedSettings() = runTest {
        feature.siteSpecificFixes().setRawStoredState(State(enable = true, settings = adapter.toJson(POPULATED_CONFIG)))
        val settings = testee.getConfig()
        assertTrue(settings.canApplySiteSpecificFixes)
        assertIsPopulatedJson(settings.javascriptConfigSiteSpecificFixes)
    }

    private fun assertIsEmptyJson(json: String) {
        assertEquals(JAVASCRIPT_CONFIG_DEFAULT, json)
    }

    private fun assertIsPopulatedJson(json: String) {
        assertEquals(POPULATED_CONFIG_JSON, json)
    }

    companion object {
        private val POPULATED_CONFIG = JSONObject().also {
            it.put("key", "value")
        }
        private const val POPULATED_CONFIG_JSON = """{"key":"value"}"""
    }
}
