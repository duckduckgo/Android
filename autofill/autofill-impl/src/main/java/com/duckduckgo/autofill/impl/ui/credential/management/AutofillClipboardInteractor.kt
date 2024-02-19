/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.management

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillClipboardInteractor {
    fun copyToClipboard(toCopy: String, isSensitive: Boolean)
}

@ContributesBinding(ActivityScope::class)
class RealAutofillClipboardInteractor @Inject constructor(
    context: Context,
) : AutofillClipboardInteractor {
    private val clipboardManager by lazy { context.getSystemService(ClipboardManager::class.java) }

    @SuppressLint("NewApi")
    override fun copyToClipboard(toCopy: String, isSensitive: Boolean) {
        val clipData = ClipData.newPlainText("", toCopy)
        if (isSensitive) {
            clipData.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboardManager.setPrimaryClip(clipData)
    }
}
