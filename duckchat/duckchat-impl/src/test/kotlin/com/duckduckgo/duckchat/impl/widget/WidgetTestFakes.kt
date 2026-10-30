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

package com.duckduckgo.duckchat.impl.widget

import android.content.Intent
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.app.tabs.NativeInputLaunchAction
import org.junit.Assert.assertEquals
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private const val EXTRA_URL = "url"
private const val EXTRA_ACTION = "action"
private const val EXTRA_AUTO_FOCUS = "autoFocus"

/** A [BrowserNav] whose widget launch intents expose their arguments as extras, so tests can assert on them. */
internal fun fakeBrowserNav(): BrowserNav = mock<BrowserNav>().also { nav ->
    whenever(nav.openDuckChat(any(), any(), any(), anyOrNull(), any(), any(), anyOrNull())).thenAnswer {
        Intent()
            .putExtra(EXTRA_URL, it.getArgument<String>(2))
            .putExtra(EXTRA_ACTION, it.getArgument<NativeInputLaunchAction?>(3)?.name)
            .putExtra(EXTRA_AUTO_FOCUS, it.getArgument<Boolean>(4))
    }
}

internal fun fakeDuckAiWidgetIntents(): DuckAiWidgetIntents = DuckAiWidgetIntents(fakeBrowserNav())

internal fun assertLaunch(
    intent: Intent,
    url: String? = null,
    action: NativeInputLaunchAction? = null,
    autoFocus: Boolean = false,
) {
    assertEquals(url, intent.getStringExtra(EXTRA_URL))
    assertEquals(action?.name, intent.getStringExtra(EXTRA_ACTION))
    assertEquals(autoFocus, intent.getBooleanExtra(EXTRA_AUTO_FOCUS, false))
}
