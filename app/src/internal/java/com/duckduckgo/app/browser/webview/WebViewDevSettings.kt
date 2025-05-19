/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.webview

/*
 * Copyright (c) 2023 DuckDuckGo
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

import android.content.Context
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.internal.features.api.InternalFeaturePlugin.Companion.WEB_VIEW_DEV_SETTINGS_PRIO_KEY
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@PriorityKey(WEB_VIEW_DEV_SETTINGS_PRIO_KEY)
class WebViewDevSettings @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val context: Context,
) : InternalFeaturePlugin {

    override fun internalFeatureTitle(): String {
        return context.getString(R.string.webview_internal_feature_title)
    }

    override fun internalFeatureSubtitle(): String {
        return context.getString(R.string.webview_internal_feature_subtitle)
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        globalActivityStarter.start(activityContext, WebViewDevSettingsScreen)
    }
}
