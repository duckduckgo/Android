/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import java.lang.*
import javax.inject.*

interface Clipboard {
    fun copyToClipboard(toCopy: String)
    fun pasteFromClipboard(): String
}

@ContributesBinding(ActivityScope::class)
class RealClipboard @Inject constructor(
    context: Context,
) : Clipboard {

    private val clipboardManager by lazy { context.getSystemService(ClipboardManager::class.java) }

    override fun copyToClipboard(toCopy: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", toCopy))
    }

    override fun pasteFromClipboard(): String {
        return if (clipboardManager.hasPrimaryClip()) {
            clipboardManager.primaryClip?.getItemAt(0)?.text.takeUnless { it.isNullOrBlank() }?.toString() ?: ""
        } else {
            ""
        }
    }
}
