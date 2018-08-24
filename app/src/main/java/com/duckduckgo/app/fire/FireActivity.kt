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

import android.animation.Animator
import android.app.Activity
import android.app.ActivityManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.support.v4.app.ActivityOptionsCompat
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import kotlinx.android.synthetic.main.activity_fire.*
import javax.inject.Inject

/**
 * Activity which is responsible for killing the main process and restarting it. This Activity will automatically finish itself after a brief time.
 *
 * This Activity runs in a separate process so that it can seamlessly restart the main app process without much in the way of a jarring UX.
 *
 * The correct way to invoke this Activity is through its `triggerRestart(context)` method.
 *
 * This Activity was largely inspired by https://github.com/JakeWharton/ProcessPhoenix
 *
 * We need to detect the user leaving this activity and possibly returning to it:
 *     if the user left our app to do something else, restarting our browser activity would feel wrong
 *     if the user left our app but came back, we should restart the browser activity
 */
class FireActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: FireViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(FireViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fire)
        if (savedInstanceState == null) {

            fireAnimationView.addAnimatorListener(object : LottieAnimationListener() {
                override fun onAnimationStart(p0: Animator?) {
                    viewModel.startDeathClock()
                }
            })
        }

        viewModel.viewState.observe(this, Observer<FireViewModel.ViewState> {
            it?.let { viewState ->
                if (!viewState.animate) {

                    // restart the app only if the user hasn't navigated away during the fire animation
                    if (viewState.autoStart) {
                        val intent = intent.getParcelableExtra<Intent>(KEY_RESTART_INTENTS)
                        startActivity(intent, activityFadeOptions(this))
                    }

                    viewModel.viewState.removeObservers(this)
                    finish()
                    killProcess()
                }
            }
        })
    }

    override fun onStop() {
        super.onStop()

        if (!isChangingConfigurations) {
            viewModel.onViewStopped()
        }
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.onViewRestarted()
    }

    override fun onBackPressed() {
        // do nothing - the activity will kill itself soon enough
    }

    companion object {
        private const val KEY_RESTART_INTENTS = "KEY_RESTART_INTENTS"

        fun triggerRestart(context: Context) {
            triggerRestart(context, getRestartIntent(context))
        }

        private fun triggerRestart(context: Context, nextIntent: Intent) {
            val intent = Intent(context, FireActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(KEY_RESTART_INTENTS, nextIntent)

            context.startActivity(intent, activityFadeOptions(context))
            if (context is Activity) {
                context.finish()
            }
            killProcess()
        }

        private fun getRestartIntent(context: Context): Intent {
            val intent = BrowserActivity.intent(context, launchedFromFireAction = true)
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

        private fun activityFadeOptions(context: Context): Bundle? {
            val config = ActivityOptionsCompat.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out)
            return config.toBundle()
        }
    }
}
