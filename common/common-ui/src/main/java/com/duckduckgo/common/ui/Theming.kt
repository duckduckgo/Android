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

package com.duckduckgo.common.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.duckduckgo.common.ui.DuckDuckGoTheme.DARK
import com.duckduckgo.common.ui.DuckDuckGoTheme.SYSTEM_DEFAULT
import com.duckduckgo.common.ui.Theming.Constants.BROADCAST_THEME_CHANGED
import com.duckduckgo.common.ui.Theming.Constants.FIXED_THEME_ACTIVITIES
import com.duckduckgo.mobile.android.R

enum class DuckDuckGoTheme {
    SYSTEM_DEFAULT,
    DARK,
    LIGHT,
    ;

    fun getOptionIndex(): Int {
        return when (this) {
            SYSTEM_DEFAULT -> 1
            LIGHT -> 2
            DARK -> 3
        }
    }
}

object Theming {

    fun getThemedDrawable(
        context: Context,
        drawableId: Int,
        theme: DuckDuckGoTheme,
    ): Drawable? {
        val themeId = when (theme) {
            SYSTEM_DEFAULT -> context.getSystemDefaultTheme()
            DARK -> R.style.Theme_DuckDuckGo_Dark
            else -> R.style.Theme_DuckDuckGo_Light
        }
        return ResourcesCompat.getDrawable(
            context.resources,
            drawableId,
            ContextThemeWrapper(context, themeId).theme,
        )
    }

    object Constants {
        const val BROADCAST_THEME_CHANGED = "BROADCAST_THEME_CHANGED"
        val FIXED_THEME_ACTIVITIES = listOf(
            "com.duckduckgo.app.onboarding.ui.OnboardingActivity",
            "com.duckduckgo.sync.impl.ui.SyncLoginActivity",
            "com.duckduckgo.sync.impl.ui.SyncConnectActivity",
            "com.duckduckgo.sync.impl.ui.ShowQRCodeActivity",
            "com.duckduckgo.sync.impl.ui.EnterCodeActivity",
            "com.duckduckgo.sync.impl.ui.SyncWithAnotherDeviceActivity",
        )
    }
}

fun AppCompatActivity.applyTheme(theme: DuckDuckGoTheme): BroadcastReceiver? {
    if (!FIXED_THEME_ACTIVITIES.contains(this.localClassName)) {
        setTheme(getThemeId(theme))
    }
    return registerForThemeChangeBroadcast()
}

fun AppCompatActivity.getThemeId(theme: DuckDuckGoTheme): Int {
    return when (theme) {
        SYSTEM_DEFAULT -> getSystemDefaultTheme()
        DARK -> R.style.Theme_DuckDuckGo_Dark
        else -> R.style.Theme_DuckDuckGo_Light
    }
}

private fun Context.getSystemDefaultTheme(): Int {
    return if (isInNightMode()) {
        R.style.Theme_DuckDuckGo_Dark
    } else {
        R.style.Theme_DuckDuckGo_Light
    }
}

private fun Context.isInNightMode(): Boolean {
    val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mode == Configuration.UI_MODE_NIGHT_YES
}

// Move this to LiveData/Flow and use appDelegate for night/day theming
private fun AppCompatActivity.registerForThemeChangeBroadcast(): BroadcastReceiver {
    val manager = LocalBroadcastManager.getInstance(applicationContext)
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                recreate()
            }
        }
    manager.registerReceiver(receiver, IntentFilter(BROADCAST_THEME_CHANGED))
    return receiver
}

fun AppCompatActivity.sendThemeChangedBroadcast() {
    val manager = LocalBroadcastManager.getInstance(applicationContext)
    manager.sendBroadcast(Intent(BROADCAST_THEME_CHANGED))
}
