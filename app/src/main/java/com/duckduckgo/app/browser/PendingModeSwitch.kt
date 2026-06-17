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
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import com.duckduckgo.browsermode.api.BrowserMode

/**
 * An action to run once the browser is in a given [BrowserMode]. Must be bundle-encodable (see
 * [toBundle] / [Bundle.toPendingModeSwitch]) so it can survive the activity recreation that a mode
 * switch triggers. Dispatched by `BrowserActivity.switchModeThen`.
 */
internal sealed class PendingAction {
    /**
     * Re-process [intent] (BrowserActivity's own launch Intent) by reading its extras. The intent
     * must be *consumed*, never re-launched — see the security invariant in `BrowserActivity.runAction`.
     */
    data class ProcessIntent(val intent: Intent) : PendingAction()
    data class OpenNewTab(
        val query: String?,
        val sourceTabId: String?,
        val skipHome: Boolean,
        val isExternal: Boolean,
    ) : PendingAction()
    data class OpenExistingTab(val tabId: String) : PendingAction()
}

/** A [PendingAction] paired with the [BrowserMode] it must run in. */
internal data class PendingModeSwitch(
    val targetMode: BrowserMode,
    val action: PendingAction,
)

internal fun PendingModeSwitch.toBundle(): Bundle {
    val bundle = bundleOf(KEY_TARGET_MODE to targetMode.name)
    when (val pendingAction = action) {
        is PendingAction.ProcessIntent -> {
            bundle.putString(KEY_ACTION, ACTION_PROCESS_INTENT)
            bundle.putParcelable(KEY_INTENT, pendingAction.intent)
        }
        is PendingAction.OpenNewTab -> {
            bundle.putString(KEY_ACTION, ACTION_OPEN_NEW_TAB)
            bundle.putString(KEY_QUERY, pendingAction.query)
            bundle.putString(KEY_SOURCE_TAB_ID, pendingAction.sourceTabId)
            bundle.putBoolean(KEY_SKIP_HOME, pendingAction.skipHome)
            bundle.putBoolean(KEY_IS_EXTERNAL, pendingAction.isExternal)
        }
        is PendingAction.OpenExistingTab -> {
            bundle.putString(KEY_ACTION, ACTION_OPEN_EXISTING_TAB)
            bundle.putString(KEY_EXISTING_TAB_ID, pendingAction.tabId)
        }
    }
    return bundle
}

internal fun Bundle.toPendingModeSwitch(): PendingModeSwitch? {
    val targetMode = getString(KEY_TARGET_MODE)?.let { runCatching { BrowserMode.valueOf(it) }.getOrNull() } ?: return null
    val action = when (getString(KEY_ACTION)) {
        ACTION_PROCESS_INTENT -> {
            val intent = BundleCompat.getParcelable(this, KEY_INTENT, Intent::class.java) ?: return null
            PendingAction.ProcessIntent(intent)
        }
        ACTION_OPEN_NEW_TAB -> PendingAction.OpenNewTab(
            query = getString(KEY_QUERY),
            sourceTabId = getString(KEY_SOURCE_TAB_ID),
            skipHome = getBoolean(KEY_SKIP_HOME),
            isExternal = getBoolean(KEY_IS_EXTERNAL),
        )
        ACTION_OPEN_EXISTING_TAB -> PendingAction.OpenExistingTab(
            tabId = getString(KEY_EXISTING_TAB_ID) ?: return null,
        )
        else -> return null
    }
    return PendingModeSwitch(targetMode, action)
}

private const val KEY_TARGET_MODE = "pendingModeSwitchTargetMode"
private const val KEY_ACTION = "pendingModeSwitchAction"
private const val KEY_INTENT = "pendingModeSwitchIntent"
private const val KEY_QUERY = "pendingModeSwitchQuery"
private const val KEY_SOURCE_TAB_ID = "pendingModeSwitchSourceTabId"
private const val KEY_SKIP_HOME = "pendingModeSwitchSkipHome"
private const val KEY_IS_EXTERNAL = "pendingModeSwitchIsExternal"
private const val KEY_EXISTING_TAB_ID = "pendingModeSwitchExistingTabId"
private const val ACTION_PROCESS_INTENT = "processIntent"
private const val ACTION_OPEN_NEW_TAB = "openNewTab"
private const val ACTION_OPEN_EXISTING_TAB = "openExistingTab"
