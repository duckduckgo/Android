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

package com.duckduckgo.widget

import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.duckduckgo.app.voice.VoiceSearchAvailabilityUtil

fun configureVoiceSearch(
    context: Context,
    remoteViews: RemoteViews,
    fromFavWidget: Boolean
) {
    if (VoiceSearchAvailabilityUtil.shouldShowVoiceSearchEntry(context)) {
        remoteViews.setViewVisibility(R.id.voiceSearch, View.VISIBLE)
        remoteViews.setViewVisibility(R.id.search, View.GONE)
        remoteViews.setOnClickPendingIntent(R.id.voiceSearch, buildVoiceSearchPendingIntent(context, fromFavWidget))
    } else {
        remoteViews.setViewVisibility(R.id.voiceSearch, View.GONE)
        remoteViews.setViewVisibility(R.id.search, View.VISIBLE)
    }
}

private fun buildVoiceSearchPendingIntent(
    context: Context,
    fromFavWidget: Boolean
): PendingIntent {
    val intent = if (fromFavWidget) SystemSearchActivity.fromFavWidget(context, true) else SystemSearchActivity.fromWidget(context, true)
    return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}
