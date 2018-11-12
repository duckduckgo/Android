/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserTabViewModel
import com.duckduckgo.app.browser.R
import java.util.*
import javax.inject.Inject


class ShortcutBuilder @Inject constructor() {

    fun buildPinnedPageShortcut(context: Context, homeShortcut: BrowserTabViewModel.Command.AddHomeShortcut): ShortcutInfoCompat {
        val intent = Intent(context, BrowserActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.putExtra(Intent.EXTRA_TEXT, homeShortcut.url)

        val icon = if (homeShortcut.icon != null) {
            IconCompat.createWithBitmap(homeShortcut.icon)
        } else {
            IconCompat.createWithResource(context, R.drawable.logo_mini)
        }

        return ShortcutInfoCompat.Builder(context, UUID.randomUUID().toString())
            .setShortLabel(homeShortcut.title)
            .setIntent(intent)
            .setIcon(icon)
            .build()
    }
}