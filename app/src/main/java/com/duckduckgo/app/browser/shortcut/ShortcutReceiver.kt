/*
 * Copyright (c) 2020 DuckDuckGo
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder.Companion.SHORTCUT_TITLE_ARG
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder.Companion.SHORTCUT_URL_ARG
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.useourapp.UseOurAppDetector
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class ShortcutReceiver @Inject constructor(
    private val dispatcher: DispatcherProvider,
    private val useOurAppDetector: UseOurAppDetector,
    private val pixel: Pixel
) :
    BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val originUrl = intent?.getStringExtra(SHORTCUT_URL_ARG)
        val title = intent?.getStringExtra(SHORTCUT_TITLE_ARG)

        if (!IGNORE_MANUFACTURERS_LIST.contains(Build.MANUFACTURER)) {
            context?.let {
                Toast.makeText(it, it.getString(R.string.useOurAppShortcutAddedText, title), Toast.LENGTH_SHORT).show()
            }
        }

        GlobalScope.launch(dispatcher.io()) {
            if (useOurAppDetector.isUseOurAppUrl(originUrl)) {
                pixel.fire(Pixel.PixelName.USE_OUR_APP_SHORTCUT_ADDED)
            } else {
                pixel.fire(Pixel.PixelName.SHORTCUT_ADDED)
            }
        }
    }

    companion object {
        val IGNORE_MANUFACTURERS_LIST = listOf("samsung", "huawei")
    }
}
