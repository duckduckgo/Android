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
import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.app.tabs.DuckChatLaunchOrigin
import org.junit.Assert.assertEquals
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.KClass

private const val EXTRA_URL = "url"
private const val EXTRA_LAUNCH = "launch"
private const val EXTRA_ORIGIN = "origin"

private fun DuckChatLaunch.origin(): DuckChatLaunchOrigin? = when (this) {
    is DuckChatLaunch.Open -> origin
    is DuckChatLaunch.ImageGeneration -> origin
    is DuckChatLaunch.CameraCapture -> origin
    is DuckChatLaunch.ChatHistory -> origin
    is DuckChatLaunch.ContextualChat, is DuckChatLaunch.TextSelection -> null
}

/** A [BrowserNav] whose widget launch intents expose arguments as extras, so tests can assert on them. */
internal fun fakeBrowserNav(): BrowserNav = mock<BrowserNav>().also { nav ->
    whenever(nav.openDuckChat(any(), any(), anyOrNull(), any())).thenAnswer {
        val launch = it.getArgument<DuckChatLaunch>(3)
        Intent()
            .putExtra(EXTRA_URL, it.getArgument<String?>(2))
            .putExtra(EXTRA_LAUNCH, launch::class.simpleName)
            .putExtra(EXTRA_ORIGIN, launch.origin()?.name)
    }
}

internal fun fakeDuckAiWidgetIntents(): DuckAiWidgetIntents = DuckAiWidgetIntents(fakeBrowserNav())

internal fun assertLaunch(
    intent: Intent,
    url: String? = null,
    launch: KClass<out DuckChatLaunch> = DuckChatLaunch.Open::class,
) {
    assertEquals(url, intent.getStringExtra(EXTRA_URL))
    assertEquals(launch.simpleName, intent.getStringExtra(EXTRA_LAUNCH))
    assertEquals(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET.name, intent.getStringExtra(EXTRA_ORIGIN))
}
