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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogCta
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w480dp-h1040dp-mdpi")
class OnboardingSelectionContentTest {
    @get:Rule val compose = createComposeRule()
    private val store = ContentValueStore()
    private val interactions = mutableListOf<ContentInteraction>()
    private val results = mutableListOf<NewUserOnboardingEvent>()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun quickSetupRecordsSwitchBeforeExternalActionAndSilentlyRendersSystemReturn() {
        val config = quickSetup()
        val state = session(config) { interaction ->
            when (interaction) {
                is ContentInteraction.QuickSetupSetDefaultBrowser -> assertEquals(
                    interaction.checked,
                    store.contentState("test", config).value.defaultBrowserChecked,
                )
                is ContentInteraction.QuickSetupAddWidget -> assertEquals(interaction.checked, store.contentState("test", config).value.widgetChecked)
                else -> Unit
            }
        }
        show(state)
        compose.onNodeWithText(context.getString(R.string.preOnboardingReinstallSetDefaultLabel)).performClick().assertIsOn()
        compose.onNodeWithText(context.getString(R.string.preOnboardingReinstallAddHomeScreenWidgetLabel)).performClick().assertIsOn()
        assertEquals(listOf(ContentInteraction.QuickSetupSetDefaultBrowser(true), ContentInteraction.QuickSetupAddWidget(true)), interactions)
        compose.runOnIdle { state.stateFor(config).value = QuickSetupContentState(false, false, OmnibarType.SINGLE_BOTTOM, false) }
        compose.onNodeWithText(context.getString(R.string.preOnboardingReinstallSetDefaultLabel)).assertIsOff()
        compose.onNodeWithText(context.getString(R.string.preOnboardingReinstallAddHomeScreenWidgetLabel)).assertIsOff()
        compose.onNodeWithText(context.getString(R.string.preOnboardingAddressBarPositionBottom)).performClick()
        compose.onNodeWithText(context.getString(R.string.quickSetupInputScreenSearchOnly)).performClick()
        assertEquals(ContentInteraction.QuickSetupEditAddressBarPosition, interactions[2])
        assertEquals(ContentInteraction.QuickSetupEditSearchOptions, interactions[3])
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf(NewUserOnboardingEvent.QuickSetupConfirmed(OmnibarType.SINGLE_BOTTOM, false)), results)
    }

    @Test
    fun everyQuickSetupVisibilityCombinationKeepsAddressPositionAndHidesOnlySpecifiedRows() {
        val current = mutableStateOf(session(quickSetup()))
        show { current.value }
        repeat(8) { flags ->
            compose.runOnIdle {
                current.value = session(
                    quickSetup().copy(
                        hideSetDefaultBrowserRow = flags and 1 != 0,
                        hideAddWidgetRow = flags and 2 != 0, hideAddressBarRow = flags and 4 != 0,
                    ),
                )
            }
            compose.onNodeWithText(context.getString(R.string.preOnboardingReinstallAddressBarLabel)).assertExists()
            val labels = listOf(
                R.string.preOnboardingReinstallSetDefaultLabel,
                R.string.preOnboardingReinstallAddHomeScreenWidgetLabel,
                R.string.preOnboardingReinstallAddressBarSearchOptionsLabel,
            )
            labels.forEachIndexed { index, label ->
                val node = compose.onNodeWithText(context.getString(label))
                if (flags and (1 shl index) == 0) node.assertExists() else node.assertDoesNotExist()
            }
        }
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun downloadReasonGatesFirstCtaAndSubmitsEveryCurrentChoiceIncludingRestoration() {
        val config = ContentConfig.DownloadReason(TextConfig.Literal("Download"), TextConfig.Literal("Choose a reason"))
        val state = session(config)
        assertFalse(state.primaryEnabled)
        show(state)
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        DownloadReasonSelection.entries.forEach { option ->
            compose.onNodeWithText(context.getString(downloadReasonLabel(option))).performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Continue").performScrollTo().assertIsEnabled().performClick()
            assertEquals(NewUserOnboardingEvent.DownloadReasonConfirmed(option), results.last())
        }
        assertTrue(session(config).primaryEnabled)
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun dependenciesRetainHiddenChildValuesAndRapidToggleCancelsObsoleteRemoval() {
        val parent = OnboardingPreference.REJECT_OPTIONAL_COOKIES
        val child = OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES
        val config = ContentConfig.PreferenceSelector(
            TextConfig.Literal("Preferences"),
            listOf(
                ContentConfig.PreferenceSelector.Row(parent, null, TextConfig.Literal("Parent"), null, true),
                ContentConfig.PreferenceSelector.Row(child, null, TextConfig.Literal("Child"), TextConfig.Literal("Child details"), true, parent),
            ),
            TextConfig.Literal("Caption"),
        )
        val state = session(config)
        show(state)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Parent").performClick()
        advance(100)
        compose.onNodeWithText("Child").assertDoesNotExist()
        compose.onNodeWithText("Parent").performClick()
        advance(250)
        compose.onNodeWithText("Child").assertIsOn().performClick()
        compose.onNodeWithText("Parent").performClick()
        advance(250)
        assertEquals(false, state.stateFor(config).value.enabled[child])
        compose.onNodeWithText("Parent").performClick()
        advance(250)
        compose.onNodeWithText("Child").assertIsOff()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(NewUserOnboardingEvent.PreferenceSelectorConfirmed(mapOf(parent to true, child to false)), results.single())
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun singleChoiceStartsWithFirstThenSubmitsCurrentAndRestoredChoice() {
        val first = TestOption("first", "First")
        val second = TestOption("second", "Second")
        val config = ContentConfig.SingleChoice(TextConfig.Literal("Choice"), TextConfig.Literal("Choose one"), listOf(first, second))
        val state = session(config)
        show(state)
        compose.onNodeWithText("First").assertIsSelected()
        compose.onNodeWithText("Second").performClick().assertIsSelected()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(NewUserOnboardingEvent.SingleChoiceConfirmed(second), results.single())
        assertEquals(second, session(config).stateFor(config).value.selected)
        compose.runOnIdle { state.stateFor(config).value = SingleChoiceContentState(first) }
        compose.onNodeWithText("First").assertIsSelected()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(NewUserOnboardingEvent.SingleChoiceConfirmed(first), results.last())
        assertTrue(interactions.isEmpty())
    }

    private fun quickSetup() = ContentConfig.QuickSetup(TextConfig.Literal("Quick setup"), true, false, false, false, OmnibarType.SINGLE_TOP, true)

    private fun session(config: ContentConfig, inspect: (ContentInteraction) -> Unit = {}) = OnboardingContentState("test", config, store, {
        inspect(it)
        interactions += it
    }, true)

    private fun show(state: OnboardingContentState) = show { state }

    private fun show(current: () -> OnboardingContentState) {
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    val state = current()
                    DaxOnboardingDialog(
                        contentKey = state.config.toString(),
                        title = state.resolvedTitle(LocalContext.current),
                        animate = false,
                        boundsAnimationDuration = state.boundsDuration,
                        primaryCta = DaxOnboardingDialogCta("Continue", state.primaryEnabled) { state.submit()?.let(results::add) },
                        content = {
                            onContentReady(state::ready)
                            OnboardingDialogContent(state, enabled)
                        },
                    )
                }
            }
        }
    }

    private fun advance(milliseconds: Long) {
        repeat(((milliseconds + 15) / 16).toInt()) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private data class TestOption(override val id: String, override val label: String, override val iconRes: Int? = null) : Option
}
