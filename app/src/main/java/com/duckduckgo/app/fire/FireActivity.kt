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
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.mode.FireRestart
import com.duckduckgo.common.ui.view.fadeTransitionConfig
import com.duckduckgo.common.ui.view.noAnimationConfig
import com.duckduckgo.di.scopes.ActivityScope

/**
 * Activity which is responsible for killing the main process and restarting it. This Activity will automatically finish itself after a brief time.
 *
 * This Activity runs in a separate process so that it can seamlessly restart the main app process without much in the way of a jarring UX.
 *
 * The correct way to invoke this Activity is through its `triggerRestart(context)` method.
 *
 * This Activity was largely inspired by https://github.com/JakeWharton/ProcessPhoenix
 */
@InjectWith(ActivityScope::class)
class FireActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reconstruct the restart Intent in this process from the primitive extras passed by triggerRestart.
        val notifyDataCleared = intent.getBooleanExtra(KEY_NOTIFY_DATA_CLEARED, false)
        val deletedTabCount = intent.getIntExtra(KEY_DELETED_TAB_COUNT, 0)
        startActivity(getRestartIntent(this, notifyDataCleared, deletedTabCount), fadeTransitionConfig())
        overridePendingTransition(0, 0)
        finish()
        killProcess()
    }

    companion object {
        private const val KEY_NOTIFY_DATA_CLEARED = "KEY_NOTIFY_DATA_CLEARED"
        private const val KEY_DELETED_TAB_COUNT = "KEY_DELETED_TAB_COUNT"

        fun triggerRestart(
            context: Context,
            notifyDataCleared: Boolean,
            enableTransitionAnimation: Boolean = true,
            deletedTabCount: Int = 0,
        ) {
            val intent = fireActivityIntent(context, notifyDataCleared, deletedTabCount)

            val transitionAnimationConfig = if (enableTransitionAnimation) {
                context.fadeTransitionConfig()
            } else {
                context.noAnimationConfig()
            }

            context.startActivity(intent, transitionAnimationConfig)
            if (context is Activity) {
                context.overridePendingTransition(0, 0)
                context.finish()
            }
            killProcess()
        }

        /**
         * Builds the Intent that launches [FireActivity], carrying only primitive extras and never a
         * nested Intent. [FireActivity] reconstructs the restart Intent itself, because forwarding a
         * nested Intent unparceled from another Intent's extras trips Android 16's intent-redirection
         * hardening (UnsafeIntentLaunchViolation).
         */
        @VisibleForTesting
        internal fun fireActivityIntent(
            context: Context,
            notifyDataCleared: Boolean,
            deletedTabCount: Int,
        ): Intent {
            return Intent(context, FireActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(KEY_NOTIFY_DATA_CLEARED, notifyDataCleared)
                putExtra(KEY_DELETED_TAB_COUNT, deletedTabCount)
            }
        }

        private fun getRestartIntent(
            context: Context,
            notifyDataCleared: Boolean = false,
            deletedTabCount: Int = 0,
        ): Intent {
            val intent = BrowserActivity.intent(
                context,
                launchSource = FireRestart,
                notifyDataCleared = notifyDataCleared,
                isLaunchFromClearDataAction = true,
                deletedTabCount = deletedTabCount,
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return intent
        }

        private fun killProcess() {
            Runtime.getRuntime().exit(0)
        }
    }
}
