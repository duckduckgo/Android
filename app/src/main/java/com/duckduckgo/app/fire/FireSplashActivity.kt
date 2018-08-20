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
import android.os.Handler
import android.os.Process
import androidx.core.os.postDelayed
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import timber.log.Timber

/**
 * Activity which is responsible for killing the main process and restarting it. This Activity will automatically finish itself after a brief time.
 *
 * This Activity runs in a separate process so that it can seamlessly restart the main app process without much in the way of a jarring UX.
 *
 * The correct way to invoke this Activity is through its `triggerRebirth(context)` method.
 *
 * This Activity was largely inspired by https://github.com/JakeWharton/ProcessPhoenix
 */
class FireSplashActivity : DuckDuckGoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Handler().postDelayed(ACTIVITY_FINISH_DELAY_MS) {
            val intent = intent.getParcelableExtra<Intent>(KEY_RESTART_INTENTS)
            startActivity(intent)
            finish()
            killProcess()
        }
    }

    override fun onBackPressed() {
        // do nothing - the activity will kill itself soon enough
    }

    companion object {
        private const val ACTIVITY_FINISH_DELAY_MS = 600L
        private const val KEY_RESTART_INTENTS = "KEY_RESTART_INTENTS"

        fun triggerRebirth(context: Context) {
            triggerRebirth(context, getRestartIntent(context))
        }

        private fun triggerRebirth(context: Context, nextIntent: Intent) {
            val intent = Intent(context, FireSplashActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // In case we are called with non-Activity context.
            intent.putExtra(KEY_RESTART_INTENTS, nextIntent)
            context.startActivity(intent)
            if (context is Activity) {
                context.finish()
            }
            killProcess()
        }

        private fun getRestartIntent(context: Context): Intent {
            val intent = BrowserActivity.intent(context)
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
                if(it.pid == currentProcessId && it.processName.endsWith(":fire")) {
                    Timber.e("Process ID $currentProcessId is fire process")
                    return true
                }
            }
            return false
        }
    }
}
