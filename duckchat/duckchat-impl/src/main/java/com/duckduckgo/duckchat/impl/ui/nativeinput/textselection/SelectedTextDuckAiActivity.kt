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

package com.duckduckgo.duckchat.impl.ui.nativeinput.textselection

import android.content.Intent
import android.os.Bundle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

/**
 * Exists purely to pull out the intent extra and attach the selection to Duck.ai.
 * This needs to be its own Activity so that we can customize the label that is user-facing, presented when the user selects some text.
 */
@InjectWith(ActivityScope::class)
class SelectedTextDuckAiActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var duckChatInternal: DuckChatInternal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractSelection(intent)?.let { selection ->
            startActivity(
                browserNav.openDuckChat(
                    this,
                    duckChatUrl = duckChatInternal.getDuckChatUrl(query = "", autoPrompt = false),
                    isContextual = !isExternalSelection(),
                    textSelection = selection,
                ),
            )
        }
        finish()
    }

    private fun extractSelection(intent: Intent?): String? {
        if (intent == null) return null

        val textSelection = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
        if (!textSelection.isNullOrBlank()) return textSelection

        logcat(LogPriority.WARN) { "SelectedTextDuckAiActivity launched with unexpected intent format" }
        return null
    }

    private fun isExternalSelection(): Boolean = callingPackage != packageName
}
