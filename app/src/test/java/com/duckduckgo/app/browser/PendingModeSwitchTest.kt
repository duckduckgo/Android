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

package com.duckduckgo.app.browser

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingModeSwitchTest {

    @Test
    fun whenProcessIntentEncodedThenItRoundTripsPreservingModeAndIntent() {
        val intent = Intent(Intent.ACTION_VIEW).putExtra(EXTRA_KEY, EXTRA_VALUE)
        val original = PendingModeSwitch(BrowserMode.REGULAR, PendingAction.ProcessIntent(intent))

        val restored = original.toBundle().toPendingModeSwitch()

        assertEquals(BrowserMode.REGULAR, restored?.targetMode)
        val action = restored?.action
        assertTrue(action is PendingAction.ProcessIntent)
        val restoredIntent = (action as PendingAction.ProcessIntent).intent
        assertEquals(Intent.ACTION_VIEW, restoredIntent.action)
        assertEquals(EXTRA_VALUE, restoredIntent.getStringExtra(EXTRA_KEY))
    }

    @Test
    fun whenOpenNewTabWithAllFieldsEncodedThenItRoundTripsExactly() {
        val original = PendingModeSwitch(
            targetMode = BrowserMode.FIRE,
            action = PendingAction.OpenNewTab(
                query = "duckduckgo privacy",
                sourceTabId = "tab-123",
                skipHome = true,
                isExternal = true,
            ),
        )

        val restored = original.toBundle().toPendingModeSwitch()

        assertEquals(original, restored)
    }

    @Test
    fun whenOpenNewTabWithNullQueryAndSourceTabIdEncodedThenItRoundTripsExactly() {
        val original = PendingModeSwitch(
            targetMode = BrowserMode.REGULAR,
            action = PendingAction.OpenNewTab(
                query = null,
                sourceTabId = null,
                skipHome = false,
                isExternal = false,
            ),
        )

        val restored = original.toBundle().toPendingModeSwitch()

        assertEquals(original, restored)
    }

    @Test
    fun whenBundleIsEmptyThenDecodesToNull() {
        assertNull(android.os.Bundle().toPendingModeSwitch())
    }

    @Test
    fun whenTargetModeNameIsUnknownThenDecodesToNull() {
        // Start from a valid bundle and corrupt only the mode so a wrong key would surface as a failure here.
        val bundle = openNewTabBundle()
        bundle.putString(KEY_TARGET_MODE, "NOT_A_REAL_MODE")

        assertNull(bundle.toPendingModeSwitch())
    }

    @Test
    fun whenActionIsMissingThenDecodesToNull() {
        val bundle = openNewTabBundle()
        bundle.remove(KEY_ACTION)

        assertNull(bundle.toPendingModeSwitch())
    }

    @Test
    fun whenActionIsUnknownThenDecodesToNull() {
        val bundle = openNewTabBundle()
        bundle.putString(KEY_ACTION, "someUnsupportedAction")

        assertNull(bundle.toPendingModeSwitch())
    }

    @Test
    fun whenProcessIntentActionHasNoIntentThenDecodesToNull() {
        val bundle = PendingModeSwitch(BrowserMode.REGULAR, PendingAction.ProcessIntent(Intent(Intent.ACTION_VIEW)))
            .toBundle()
        bundle.remove(KEY_INTENT)

        assertNull(bundle.toPendingModeSwitch())
    }

    @Test
    fun whenOpenExistingTabActionHasNoTabIdThenDecodesToNull() {
        val bundle = PendingModeSwitch(BrowserMode.FIRE, PendingAction.OpenExistingTab("tab-123"))
            .toBundle()
        bundle.remove(KEY_EXISTING_TAB_ID)

        assertNull(bundle.toPendingModeSwitch())
    }

    @Test
    fun whenOpenExistingTabRoundTrippedThroughBundleThenPreserved() {
        val original = PendingModeSwitch(
            targetMode = BrowserMode.FIRE,
            action = PendingAction.OpenExistingTab("tab-123"),
        )

        val restored = original.toBundle().toPendingModeSwitch()

        assertEquals(BrowserMode.FIRE, restored?.targetMode)
        assertEquals(PendingAction.OpenExistingTab("tab-123"), restored?.action)
    }

    private fun openNewTabBundle() = PendingModeSwitch(
        targetMode = BrowserMode.REGULAR,
        action = PendingAction.OpenNewTab(query = "q", sourceTabId = "t", skipHome = false, isExternal = false),
    ).toBundle()

    private companion object {
        const val EXTRA_KEY = "extra_key"
        const val EXTRA_VALUE = "extra_value"

        // Wire-format keys — mirror the private constants in PendingModeSwitch.kt. The malformed-bundle
        // tests above start from a valid bundle and mutate a single field, so a stale key here fails the
        // assertNull rather than silently passing.
        const val KEY_TARGET_MODE = "pendingModeSwitchTargetMode"
        const val KEY_ACTION = "pendingModeSwitchAction"
        const val KEY_INTENT = "pendingModeSwitchIntent"
        const val KEY_EXISTING_TAB_ID = "pendingModeSwitchExistingTabId"
    }
}
