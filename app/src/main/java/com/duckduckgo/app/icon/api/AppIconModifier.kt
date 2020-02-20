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

package com.duckduckgo.app.icon.api

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.shortcut.AppShortcutCreator
import com.duckduckgo.app.icon.api.IconModifier.Companion.QUALIFIER
import javax.inject.Inject

interface IconModifier {

    companion object {
        const val QUALIFIER = "com.duckduckgo.app.launch"
    }

    fun changeIcon(previousIcon: AppIcon, newIcon: AppIcon)
}

enum class AppIcon(
    val componentName: String, // Must correspond to the <activity-alias> `android:name`s in AndroidManifest
    @DrawableRes val icon: Int = R.mipmap.ic_launcher_red
) {
    DEFAULT(
        componentName = "$QUALIFIER.Launcher",
        icon = R.mipmap.ic_launcher_red
    ),
    BLUE(
        componentName = "$QUALIFIER.LauncherBlue",
        icon = R.mipmap.ic_launcher_blue
    ),
    BLACK(
        componentName = "$QUALIFIER.LauncherBlack",
        icon = R.mipmap.ic_launcher_black
    ),
    GOLD(
        componentName = "$QUALIFIER.LauncherGold",
        icon = R.mipmap.ic_launcher_gold
    ),
    GREEN(
        componentName = "$QUALIFIER.LauncherGreen",
        icon = R.mipmap.ic_launcher_green
    ),
    PURPLE(
        componentName = "$QUALIFIER.LauncherPurple",
        icon = R.mipmap.ic_launcher_purple
    ),
    WHITE(
        componentName = "$QUALIFIER.LauncherWhite",
        icon = R.mipmap.ic_launcher_white
    ),
    SILHOUETTE(
        componentName = "$QUALIFIER.LauncherSilhoutte",
        icon = R.mipmap.ic_launcher_silhouette
    ), ;

    companion object {
        fun from(componentName: String): AppIcon {
            return values().first { it.componentName == componentName }
        }
    }
}

class AppIconModifier @Inject constructor(
    private val context: Context,
    private val appShortcutCreator: AppShortcutCreator
) : IconModifier {

    override fun changeIcon(previousIcon: AppIcon, newIcon: AppIcon) {
        enable(newIcon)
        disable(previousIcon)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            appShortcutCreator.configureAppShortcuts(context)
        }
    }

    private fun disable(appIcon: AppIcon) {
        setComponentState(appIcon.componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    private fun enable(appIcon: AppIcon) {
        setComponentState(appIcon.componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    private fun setComponentState(componentName: String, componentState: Int) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(BuildConfig.APPLICATION_ID, componentName),
            componentState, PackageManager.DONT_KILL_APP
        )
    }
}
