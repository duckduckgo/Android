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

package com.duckduckgo.adblocking.impl.ui

import android.content.Context
import android.view.View
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.AdBlockingSettingsPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(150)
class AdBlockingSettingsEntry @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val statusChecker: AdBlockingStatusChecker,
) : AdBlockingSettingsPlugin {

    override fun getView(context: Context): View {
        if (!statusChecker.isShownInSettings()) {
            return View(context).apply { visibility = View.GONE }
        }
        return OneLineListItem(context).apply {
            setLeadingIconResource(CommonR.drawable.ic_video_player_color_24)
            setPrimaryText(context.getString(R.string.ad_blocking_settings_title))
            setOnClickListener {
                globalActivityStarter.start(this.context, AdBlockingSettingsNoParams, null)
            }
        }
    }
}
