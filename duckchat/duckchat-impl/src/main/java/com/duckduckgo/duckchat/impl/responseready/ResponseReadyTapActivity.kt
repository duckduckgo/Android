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

package com.duckduckgo.duckchat.impl.responseready

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.DuckChatInternal
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

// POC: opens the chat in its original tab if that tab still shows it, otherwise in a new tab.
@InjectWith(ActivityScope::class)
class ResponseReadyTapActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var tabRepository: TabRepository

    @Inject
    lateinit var duckChat: DuckChat

    @Inject
    lateinit var duckChatInternal: DuckChatInternal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tabId = intent.getStringExtra(EXTRA_TAB_ID)
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        if (tabId == null || chatId == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val tabUrl = tabRepository.getTab(tabId)?.url
            val tabShowsChat = tabUrl?.toUri()?.toChatIdOrNull(duckChat) == chatId
            logcat(tag = "DuckAiResponseMonitor") { "POC: tap chat=$chatId tab=$tabId tabShowsChat=$tabShowsChat" }
            val target = if (tabShowsChat) {
                browserNav.openExistingTab(this@ResponseReadyTapActivity, tabId)
            } else {
                browserNav.openInNewTab(this@ResponseReadyTapActivity, duckChatInternal.buildChatUrl(chatId))
            }
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }

    companion object {
        private const val EXTRA_TAB_ID = "EXTRA_TAB_ID"
        private const val EXTRA_CHAT_ID = "EXTRA_CHAT_ID"

        fun intent(context: Context, tabId: String, chatId: String): Intent =
            Intent(context, ResponseReadyTapActivity::class.java)
                .putExtra(EXTRA_TAB_ID, tabId)
                .putExtra(EXTRA_CHAT_ID, chatId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
