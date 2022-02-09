/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.widget.AppWidgetManagerAddWidgetLauncher.Companion.ACTION_ADD_WIDGET
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class WidgetAddedReceiver @Inject constructor(
    private val context: Context
) : BroadcastReceiver(), DefaultLifecycleObserver {

    companion object {
        val IGNORE_MANUFACTURERS_LIST = listOf("samsung", "huawei")
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        context.registerReceiver(this, IntentFilter(ACTION_ADD_WIDGET))
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        context.unregisterReceiver(this)
    }

    override fun onReceive(
        context: Context?,
        intent: Intent?
    ) {
        if (!IGNORE_MANUFACTURERS_LIST.contains(Build.MANUFACTURER)) {
            context?.let {
                val title = intent?.getStringExtra(AppWidgetManagerAddWidgetLauncher.EXTRA_WIDGET_ADDED_LABEL) ?: ""
                Toast.makeText(it, it.getString(R.string.homeScreenWidgetAdded, title), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
