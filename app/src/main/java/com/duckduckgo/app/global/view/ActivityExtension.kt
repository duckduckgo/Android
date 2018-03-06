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

package com.duckduckgo.app.global.view

import android.content.Intent
import android.support.v4.app.FragmentActivity
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.duckduckgo.app.browser.R
import org.jetbrains.anko.toast

fun FragmentActivity.launchExternalActivity(intent: Intent) {
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        toast(R.string.no_compatible_third_party_app_installed)
    }
}

fun FragmentActivity.toggleFullScreen() {

    val newUiOptions = window.decorView.systemUiVisibility
            .xor(android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            .xor(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN)
            .xor(android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

    window.decorView.systemUiVisibility = newUiOptions
}

fun FragmentActivity.isImmersiveModeEnabled(): Boolean {
    val uiOptions = window.decorView.systemUiVisibility
    return uiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY == uiOptions
}
