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

package com.duckduckgo.autofill.impl.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.slice.Slice
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build.VERSION_CODES
import android.service.autofill.InlinePresentation
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AutofillServiceViewProvider {
    fun createFormPresentation(
        context: Context,
        suggestionTitle: String,
        suggestionSubtitle: String,
        icon: Int,
    ): RemoteViews

    fun createInlinePresentation(
        context: Context,
        pendingIntent: PendingIntent,
        suggestionTitle: String,
        suggestionSubtitle: String,
        icon: Int,
        inlinePresentationSpec: InlinePresentationSpec,
    ): InlinePresentation?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutofillServiceViewProvider @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : AutofillServiceViewProvider {

    override fun createFormPresentation(
        context: Context,
        suggestionTitle: String,
        suggestionSubtitle: String,
        icon: Int,
    ) = buildAutofillRemoteViews(
        context = context,
        name = suggestionTitle,
        subtitle = suggestionSubtitle,
        iconRes = icon,
    )

    @RequiresApi(VERSION_CODES.R)
    override fun createInlinePresentation(
        context: Context,
        pendingIntent: PendingIntent,
        suggestionTitle: String,
        suggestionSubtitle: String,
        icon: Int,
        inlinePresentationSpec: InlinePresentationSpec,
    ): InlinePresentation? {
        val isInlineSupported = isInlineSuggestionSupported(inlinePresentationSpec)
        if (isInlineSupported) {
            val slice = createSlice(context, pendingIntent, suggestionTitle, suggestionSubtitle, icon)
            return InlinePresentation(slice, inlinePresentationSpec, false)
        }
        return null
    }

    @RequiresApi(VERSION_CODES.R)
    private fun isInlineSuggestionSupported(inlinePresentationSpec: InlinePresentationSpec?): Boolean {
        // requires >= android 11
        return if (appBuildConfig.sdkInt >= 30 && inlinePresentationSpec != null) {
            UiVersions.getVersions(inlinePresentationSpec.style).contains(UiVersions.INLINE_UI_VERSION_1)
        } else {
            false
        }
    }

    @SuppressLint("RestrictedApi") // because getSlice, but docs clearly indicate you need to use that method.
    @RequiresApi(VERSION_CODES.R)
    private fun createSlice(
        context: Context,
        pendingIntent: PendingIntent,
        suggestionTitle: String,
        suggestionSubtitle: String,
        icon: Int,
    ): Slice {
        val slice = InlineSuggestionUi.newContentBuilder(
            pendingIntent,
        ).setTitle(suggestionTitle)
            .setSubtitle(suggestionSubtitle)
            .setStartIcon(Icon.createWithResource(context, icon))
            .build().slice
        return slice
    }

    private fun buildAutofillRemoteViews(
        context: Context,
        name: String,
        subtitle: String,
        @DrawableRes iconRes: Int,
    ): RemoteViews =
        RemoteViews(
            context.packageName,
            R.layout.autofill_remote_view,
        ).apply {
            setTextViewText(
                R.id.title,
                name,
            )
            setTextViewText(
                R.id.subtitle,
                subtitle,
            )
            setImageViewResource(
                R.id.ddgIcon,
                iconRes,
            )
        }
}
