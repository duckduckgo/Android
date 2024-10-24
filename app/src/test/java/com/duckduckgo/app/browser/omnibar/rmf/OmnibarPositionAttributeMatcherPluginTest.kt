package com.duckduckgo.app.browser.omnibar.rmf

import com.duckduckgo.app.browser.omnibar.ChangeOmnibarPositionFeature
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class OmnibarPositionAttributeMatcherPluginTest {
    private val settingsDataStore: SettingsDataStore = mock()
    private lateinit var plugin: OmnibarPositionAttributeMatcherPlugin

    private val omnibarPositionFeature = FakeFeatureToggleFactory.create(ChangeOmnibarPositionFeature::class.java)

    @Before
    fun before() {
        plugin = OmnibarPositionAttributeMatcherPlugin(omnibarPositionFeature, settingsDataStore)
    }

    @Test
    fun `matcher returns true when omnibarPositionFeatureEnabled attribute matches the feature flag`() = runTest {
        val attribute = OmnibarPositionFeatureEnabledMatchingAttribute(true)
        omnibarPositionFeature.self().setRawStoredState(Toggle.State(enable = true))

        val result = plugin.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun `matcher returns false when omnibarPositionFeatureEnabled attribute doesn't match the feature flag`() = runTest {
        val attribute = OmnibarPositionFeatureEnabledMatchingAttribute(true)
        omnibarPositionFeature.self().setRawStoredState(Toggle.State(enable = false))

        val result = plugin.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun `matcher returns true when omnibarPosition attribute matches the top omnibar position`() = runTest {
        val attribute = OmnibarPositionMatchingAttribute(OmnibarPosition.TOP)
        whenever(settingsDataStore.omnibarPosition).thenReturn(OmnibarPosition.TOP)

        val result = plugin.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun `matcher returns false when omnibarPosition attribute doesn't match the top omnibar position`() = runTest {
        val attribute = OmnibarPositionMatchingAttribute(OmnibarPosition.TOP)
        whenever(settingsDataStore.omnibarPosition).thenReturn(OmnibarPosition.BOTTOM)

        val result = plugin.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun `matcher returns null for unknown attribute`() = runTest {
        val unknownAttribute = object : MatchingAttribute {}

        val result = plugin.evaluate(unknownAttribute)

        assertNull(result)
    }
}
