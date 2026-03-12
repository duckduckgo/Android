package com.duckduckgo.edgetoedge.impl

import com.duckduckgo.edgetoedge.api.EdgeToEdgeFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealEdgeToEdgeTest {

    private val fakeFeature = FakeFeatureToggleFactory.create(EdgeToEdgeFeature::class.java)
    private val edgeToEdge = RealEdgeToEdge(fakeFeature)

    @Test
    fun `isEnabled returns false by default`() {
        assertFalse(edgeToEdge.isEnabled())
    }

    @Test
    fun `isEnabled returns true when feature toggle enabled`() {
        fakeFeature.self().setRawStoredState(Toggle.State(enable = true))
        assertTrue(edgeToEdge.isEnabled())
    }

    @Test
    fun `isEnabled returns false when feature toggle disabled`() {
        fakeFeature.self().setRawStoredState(Toggle.State(enable = false))
        assertFalse(edgeToEdge.isEnabled())
    }
}
