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

package com.duckduckgo.user.website.blocklist.impl.ui.settings

import android.content.Context
import android.view.View
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.ThreatProtectionSettingsPlugin
import com.duckduckgo.user.website.blocklist.impl.R
import com.duckduckgo.user.website.blocklist.impl.UserWebsiteBlocklistFeature
import com.duckduckgo.user.website.blocklist.impl.ui.UserBlockedSitesScreenNoParams
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
class UserBlockedSitesSettingsPlugin @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val feature: UserWebsiteBlocklistFeature,
) : ThreatProtectionSettingsPlugin {

    override fun getView(context: Context): View {
        val item = OneLineListItem(context)
        item.setPrimaryText(context.getString(R.string.userBlockedSitesEntry))
        item.setClickListener {
            globalActivityStarter.start(context, UserBlockedSitesScreenNoParams)
        }
        item.visibility = if (feature.self().isEnabled()) View.VISIBLE else View.GONE
        return item
    }
}
