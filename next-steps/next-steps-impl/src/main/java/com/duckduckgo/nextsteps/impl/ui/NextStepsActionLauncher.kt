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
package com.duckduckgo.nextsteps.impl.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.app.widget.WidgetType
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import logcat.LogPriority.WARN
import logcat.logcat
import java.util.UUID
import javax.inject.Inject

/**
 * Executes the Next Steps item actions on behalf of a section view. [register] must be called while the view is
 * attached and [unregister] when it detaches, so the default browser dialog result can be received.
 */
class NextStepsActionLauncher @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val addWidgetLauncher: AddWidgetLauncher,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
) {

    private val defaultBrowserDialogKey = "next_steps_default_browser_${UUID.randomUUID()}"
    private var defaultBrowserDialogLauncher: ActivityResultLauncher<Intent>? = null

    fun register(context: Context) {
        defaultBrowserDialogLauncher = (context.getActivity() as? ComponentActivity)?.activityResultRegistry?.register(
            defaultBrowserDialogKey,
            ActivityResultContracts.StartActivityForResult(),
        ) { }
    }

    fun unregister() {
        defaultBrowserDialogLauncher?.unregister()
        defaultBrowserDialogLauncher = null
    }

    fun launchAddWidget(
        context: Context,
        widgetType: WidgetType,
    ) {
        addWidgetLauncher.launchAddWidget(context, widgetType)
    }

    fun launchScreen(
        context: Context,
        screen: String,
        payload: String,
    ) {
        globalActivityStarter.start(context, DeeplinkActivityParams(screenName = screen, jsonArguments = payload), null)
    }

    /** Shows the system default browser dialog, falling back to the default apps settings. Returns true when the dialog was shown. */
    fun launchDefaultBrowserDialog(context: Context): Boolean {
        val launcher = defaultBrowserDialogLauncher
        if (launcher == null) {
            launchDefaultBrowserSettings(context)
            return false
        }

        val launched = try {
            val intent = defaultRoleBrowserDialog.createIntent(context)
            if (intent != null) {
                launcher.launch(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logcat(WARN) { "Can't launch default browser dialog" }
            false
        }

        if (!launched) {
            launchDefaultBrowserSettings(context)
        }
        return launched
    }

    fun launchDefaultBrowserSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            intent.putExtra(":settings:fragment_args_key", "default_browser")
            intent.putExtra(":settings:show_fragment_args", bundleOf(":settings:fragment_args_key" to "default_browser"))
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "Can't launch default app settings screen" }
        }
    }

    private fun Context.getActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
