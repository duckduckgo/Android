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

package com.duckduckgo.desktopapppromotion.impl

import android.content.Context
import androidx.annotation.DrawableRes
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionParams

/**
 * [DesktopAppPromotionParams] with every caller-optional field filled in. Resolving the defaults
 * needs a `Context`, so it happens in the Activity rather than the ViewModel.
 */
data class DesktopAppPromotionContent(
    val toolbarTitle: String,
    val title: String,
    val body: String,
    @DrawableRes val illustration: Int,
    val downloadUrlDisplay: String,
    val downloadUrl: String,
    val shareButtonLabel: String,
    val shareIntentTitle: String,
    val shareIntentBody: String?,
    val showDismissButton: Boolean,
    val dismissButtonLabel: String,
    val linkCopiedMessage: String,
)

fun DesktopAppPromotionParams.resolveContent(context: Context): DesktopAppPromotionContent {
    return DesktopAppPromotionContent(
        toolbarTitle = toolbarTitle ?: context.getString(R.string.desktopAppPromotionToolbarTitle),
        title = title ?: context.getString(R.string.desktopAppPromotionTitle),
        body = body ?: context.getString(R.string.desktopAppPromotionBody),
        illustration = if (illustration != 0) illustration else R.drawable.image_get_desktop_browser,
        downloadUrlDisplay = downloadUrlDisplay ?: context.getString(R.string.desktopAppPromotionUrl),
        downloadUrl = downloadUrl ?: DEFAULT_DOWNLOAD_URL,
        shareButtonLabel = shareButtonLabel ?: context.getString(R.string.desktopAppPromotionShareDownloadLink),
        shareIntentTitle = shareIntentTitle ?: context.getString(R.string.desktopAppPromotionShareDownloadLink),
        shareIntentBody = shareIntentBody,
        showDismissButton = showDismissButton,
        dismissButtonLabel = dismissButtonLabel ?: context.getString(R.string.desktopAppPromotionNoThanks),
        linkCopiedMessage = context.getString(R.string.desktopAppPromotionLinkCopied),
    )
}

// Matches the attribution the Settings entry points use; deeplink launches land on the same funnel.
private const val DEFAULT_DOWNLOAD_URL = "https://duckduckgo.com/browser?origin=funnel_appsettings_android"
