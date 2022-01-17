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

package com.duckduckgo.app.fire

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.view.fadeTransitionConfig

/**
 * Activity which is responsible for killing the main process and restarting it. This Activity will automatically finish itself after a brief time.
 *
 * This Activity runs in a separate process so that it can seamlessly restart the main app process without much in the way of a jarring UX.
 *
 * The correct way to invoke this Activity is through its `triggerRestart(context)` method.
 *
 * This Activity was largely inspired by https://github.com/JakeWharton/ProcessPhoenix
 */
class FireActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent.getParcelableExtra<Intent>(KEY_RESTART_INTENTS)
        startActivity(intent, fadeTransitionConfig())
        overridePendingTransition(0, 0)
        finish()
        killProcess()
    }

    override fun onBackPressed() {
        // do nothing - the activity will kill itself soon enough
    }

    companion object {
        private const val KEY_RESTART_INTENTS = "KEY_RESTART_INTENTS"

        fun triggerRestart(
            context: Context,
            notifyDataCleared: Boolean
        ) {
            triggerRestart(context, getRestartIntent(context, notifyDataCleared))
        }

        private fun triggerRestart(
            context: Context,
            nextIntent: Intent
        ) {
            val intent = Intent(context, FireActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(KEY_RESTART_INTENTS, nextIntent)

            context.startActivity(intent, context.fadeTransitionConfig())
            if (context is Activity) {
                context.overridePendingTransition(0, 0)
                context.finish()
            }
            killProcess()
        }

        private fun getRestartIntent(
            context: Context,
            notifyDataCleared: Boolean = false
        ): Intent {
            val intent = BrowserActivity.intent(context, notifyDataCleared = notifyDataCleared)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return intent
        }

        private fun killProcess() {
            Runtime.getRuntime().exit(0)
        }

        fun appRestarting(context: Context): Boolean {
            val currentProcessId = Process.myPid()
            val activityManager: ActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.runningAppProcesses?.forEach {
                if (it.pid == currentProcessId && it.processName.endsWith(context.getString(R.string.fireProcessName))) {
                    return true
                }
            }
            return false
        }
    }
}
