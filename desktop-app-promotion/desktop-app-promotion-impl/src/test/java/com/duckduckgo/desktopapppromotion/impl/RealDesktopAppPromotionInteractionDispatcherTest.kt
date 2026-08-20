/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.desktopapppromotion.impl

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealDesktopAppPromotionInteractionDispatcherTest {

    private val settingsHandler = FakeInteractionHandler("settings_desktop_browser")
    private val otherHandler = FakeInteractionHandler("some_other_caller")

    private val testee = RealDesktopAppPromotionInteractionDispatcher(
        handlers = FakePluginPoint(listOf(settingsHandler, otherHandler)),
    )

    @Test
    fun whenHandlerIdMatchesThenOnlyThatHandlerIsNotified() = runTest {
        testee.dispatch("settings_desktop_browser", Interaction.DISMISSED)

        assertEquals(listOf(Interaction.DISMISSED), settingsHandler.interactions)
        assertTrue(otherHandler.interactions.isEmpty())
    }

    @Test
    fun whenHandlerIdIsNullThenNoHandlerIsNotified() = runTest {
        testee.dispatch(null, Interaction.DISMISSED)

        assertTrue(settingsHandler.interactions.isEmpty())
        assertTrue(otherHandler.interactions.isEmpty())
    }

    @Test
    fun whenHandlerIdMatchesNoRegisteredHandlerThenNoHandlerIsNotified() = runTest {
        testee.dispatch("nobody_contributed_this", Interaction.SHARE_COMPLETED)

        assertTrue(settingsHandler.interactions.isEmpty())
        assertTrue(otherHandler.interactions.isEmpty())
    }

    @Test
    fun whenEveryInteractionIsDispatchedThenAllReachTheMatchingHandler() = runTest {
        testee.dispatch("settings_desktop_browser", Interaction.LINK_COPIED)
        testee.dispatch("settings_desktop_browser", Interaction.SHARE_COMPLETED)
        testee.dispatch("settings_desktop_browser", Interaction.DISMISSED)

        assertEquals(
            listOf(Interaction.LINK_COPIED, Interaction.SHARE_COMPLETED, Interaction.DISMISSED),
            settingsHandler.interactions,
        )
    }

    private class FakeInteractionHandler(override val handlerId: String) : DesktopAppPromotionInteractionHandler {
        val interactions = mutableListOf<Interaction>()

        override suspend fun onInteraction(interaction: Interaction) {
            interactions += interaction
        }
    }

    private class FakePluginPoint(
        private val plugins: List<DesktopAppPromotionInteractionHandler>,
    ) : PluginPoint<DesktopAppPromotionInteractionHandler> {
        override fun getPlugins(): Collection<DesktopAppPromotionInteractionHandler> = plugins
    }
}
