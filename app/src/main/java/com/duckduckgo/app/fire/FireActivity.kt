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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.core.os.postDelayed
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity

class FireActivity : DuckDuckGoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fire)
    }

    override fun onStart() {
        super.onStart()

        Handler().postDelayed(ACTIVITY_FINISH_DELAY_MS){
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
            val intent = Intent(context, FireActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // In case we are called with non-Activity context.
            intent.putExtra(KEY_RESTART_INTENTS, nextIntent)
            context.startActivity(intent)
            if (context is Activity) {
                context.finish()
            }
            killProcess() // Kill kill kill!
        }

        private fun getRestartIntent(context: Context): Intent {

            val intent = BrowserActivity.intent(context)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return intent

//            val defaultIntent = Intent(Intent.ACTION_MAIN, null)
//            defaultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
//            defaultIntent.addCategory(Intent.CATEGORY_DEFAULT)
//
//            val packageName = context.packageName
//            val packageManager = context.packageManager
//            for (resolveInfo in packageManager.queryIntentActivities(defaultIntent, 0)) {
//                val activityInfo = resolveInfo.activityInfo
//                if (activityInfo.packageName == packageName) {
//                    defaultIntent.component = ComponentName(packageName, activityInfo.name)
//                    return defaultIntent
//                }
//            }
//
//            throw IllegalStateException("Unable to determine default activity for $packageName. Does an activity specify the DEFAULT category in its intent filter?")
        }

        private fun killProcess() {
            Runtime.getRuntime().exit(0)
        }
    }


}
