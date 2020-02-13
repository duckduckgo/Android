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

package com.duckduckgo.app.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.settings.IconModifier.Companion.QUALIFIER
import javax.inject.Inject

interface IconModifier {

    companion object {
        const val QUALIFIER = "com.duckduckgo.app.launch"
    }

    fun changeIcon(newIcon: AppIcon)

}

enum class AppIcon(
    val componentName: String, // Must correspond to the <activity-alias> `android:name`s in AndroidManifest
    @DrawableRes val icon: Int = R.mipmap.ic_launcher_red
) {
    DEFAULT(
        componentName = "$QUALIFIER.Launcher",
        icon = R.mipmap.ic_launcher_blue
    ),
    BLUE(
        componentName = "$QUALIFIER.LauncherBlue",
        icon = R.mipmap.ic_launcher_blue
    ),
    RED(
        componentName = "$QUALIFIER.LauncherRed",
        icon = R.mipmap.ic_launcher_red
    ),
}

class AppIconModifier @Inject constructor(private val context: Context) : IconModifier {

    override fun changeIcon(newIcon: AppIcon) {
        AppIcon.values().filter { it.componentName != newIcon.componentName }.map { disable(it) }
        AppIcon.values().filter { it.componentName == newIcon.componentName }.map { enable(it) }
    }

    private fun disable(appIcon: AppIcon){
        setComponentState(appIcon.componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    private fun enable(appIcon: AppIcon){
        setComponentState(appIcon.componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    private fun setComponentState(componentName: String, componentState: Int) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, componentName),
            componentState, PackageManager.DONT_KILL_APP
        )
    }

}
