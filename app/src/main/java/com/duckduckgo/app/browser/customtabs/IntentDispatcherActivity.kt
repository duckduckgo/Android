/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.customtabs

import android.content.Intent
import android.os.Bundle
import android.provider.Browser
import androidx.browser.customtabs.CustomTabsIntent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.global.intentText
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import timber.log.Timber

@InjectWith(ActivityScope::class)
class IntentDispatcherActivity : DuckDuckGoActivity() {

    @Inject lateinit var customTabDetector: CustomTabDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("TAG_CUSTOM_TAB_IMPL onCreate called in IntentDispatcherActivity -- ${System.identityHashCode(this)}")

        customTabDetector.setCustomTab(false)

        if (intent?.hasExtra(CustomTabsIntent.EXTRA_SESSION) == true) {
            Timber.d("TAG_CUSTOM_TAB_IMPL new intent has session, show custom tab")
            Timber.d("TAG_CUSTOM_TAB_IMPL intent: $intent -- extras: ${intent.extras}")
            val pairs = intent.getBundleExtra(Browser.EXTRA_HEADERS)
            pairs?.keySet()?.forEach { key ->
                val header = pairs.getString(key)
                Timber.d("TAG_CUSTOM_TAB_IMPL header: $header -- key: $key")
            }
            showCustomTab()
        } else {
            Timber.d("TAG_CUSTOM_TAB_IMPL show browser activity")
            showBrowserActivity()
        }
    }

    private fun showCustomTab() {
        // As customizations we only support the toolbar color at the moment.
        startActivity(
            CustomTabActivity.intent(
                context = this,
                flags = Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_CLEAR_TASK and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                text = intent.intentText,
                toolbarColor = intent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0),
            ),
        )

        finish()
    }

    private fun showBrowserActivity() {
        startActivity(
            BrowserActivity.intent(
                context = this,
                queryExtra = intent.intentText,
            ),
        )

        overridePendingTransition(0, 0)
        finish()
    }
}
