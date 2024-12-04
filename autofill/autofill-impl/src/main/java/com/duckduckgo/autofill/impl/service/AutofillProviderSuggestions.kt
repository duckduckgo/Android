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
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build.VERSION_CODES
import android.service.autofill.Dataset
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.service.AutofillFieldType.UNKNOWN
import com.duckduckgo.autofill.impl.service.AutofillFieldType.USERNAME
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementActivity
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlin.random.Random
import timber.log.Timber

interface AutofillProviderSuggestions {
    suspend fun buildSuggestionsResponse(
        context: Context,
        autofillRequest: AutofillParsedRequest,
        request: FillRequest,
    ): FillResponse
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutofillProviderSuggestions @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val autofillStore: AutofillStore,
) : AutofillProviderSuggestions {

    @SuppressLint("NewApi")
    override suspend fun buildSuggestionsResponse(
        context: Context,
        autofillRequest: AutofillParsedRequest,
        request: FillRequest,
    ): FillResponse {
        var inlineSuggestionsToShow = getMaxInlinedSuggestions(request) - 1
        val node: AutofillRootNode = autofillRequest.rootNode
        Timber.i("DDGAutofillService Fillable Request for rootNode: $node")
        val fillableFields = node.parsedAutofillFields.filter { it.type != UNKNOWN }
        Timber.i("DDGAutofillService Fillable Request for fields: $fillableFields")
        // ensure fields is not empty
        // add entries
        val response = FillResponse.Builder()
        fillableFields.forEach { fieldsToAutofill ->
            val credentials = loginCredentials(node) ?: listOf(LoginCredentials(12L, "domain", "username", "password"))
            credentials?.forEach { credential ->
                val datasetBuilder = Dataset.Builder()
                Timber.i("DDGAutofillService Fillable Request for fields: $fieldsToAutofill")
                // TODO: what if we don't have username/domain/others
                val suggestionTitle = credential.username ?: "not found"
                val suggestionSubtitle = credential.domain ?: "not found"
                val icon = R.drawable.ic_dax_silhouette_primary_24
                // >= android 11
                if (appBuildConfig.sdkInt >= VERSION_CODES.R && inlineSuggestionsToShow > 0) {
                    datasetBuilder.addInlinePresentationsIfSupported(context, request, suggestionTitle, suggestionSubtitle, icon)
                    inlineSuggestionsToShow -= 1
                }
                // Supported in all android apis
                val formPresentation = createFormPresentation(context, suggestionTitle, suggestionSubtitle, icon)

                datasetBuilder.setValue(
                    fieldsToAutofill.autofillId,
                    autofillValue(credential, fieldsToAutofill.type),
                    formPresentation,
                )
                val pendingIntent = createAutofillSelectionIntent(context)
                val dataset = datasetBuilder
                    .setAuthentication(pendingIntent.intentSender) // TODO: this is how we should request auth
                    .build()
                response.addDataset(dataset)
            }
        }

        // adding access to ddg app
        val ddgAppDataSetBuild = createAccessDDGDataSet(context, request, fillableFields)
        response.addDataset(ddgAppDataSetBuild)

        // add ignored ids
        return response.build()
    }

    @SuppressLint("NewApi")
    private fun createAccessDDGDataSet(
        context: Context,
        request: FillRequest,
        fillableFields: List<ParsedAutofillField>,
    ): Dataset {
        // add access passwords
        val ddgAppDataSet = Dataset.Builder()
        val suggestionTitle = "Search in DuckDuckGo"
        val suggestionSubtitle = ""
        val icon = R.drawable.ic_dax_silhouette_primary_24
        val pendingIntent = createAutofillSelectionIntent(context)
        if (appBuildConfig.sdkInt >= VERSION_CODES.R) {
            ddgAppDataSet.addInlinePresentationsIfSupported(context, request, suggestionTitle, suggestionSubtitle, icon)
        }
        val formPresentation = createFormPresentation(context, suggestionTitle, suggestionSubtitle, icon)
        // TODO: why we add one per fillable field, is this not adding multiple entries?
        // seems in >30 we will only how 1 presentation, but what happens <30?
        // I think nothing because they are separate fields, and suggestions will only appear when field focused
        fillableFields.forEach { fieldsToAutofill ->
            ddgAppDataSet.setValue(
                fieldsToAutofill.autofillId,
                if (fieldsToAutofill.type == USERNAME) {
                    AutofillValue.forText("username")
                } else {
                    AutofillValue.forText("password")
                },
                formPresentation,
            )
        }
        // TODO: how can we require auth for <30
        val ddgAppDataSetBuild = ddgAppDataSet
            .setAuthentication(pendingIntent.intentSender)
            .build()
        return ddgAppDataSetBuild
    }

    @RequiresApi(VERSION_CODES.R)
    private fun Dataset.Builder.addInlinePresentationsIfSupported(
        context: Context,
        request: FillRequest,
        suggestionTitle: String,
        suggestionSubtitle: String,
        icon: Int,
    ) {
        val inlinePresentationSpec = request.inlineSuggestionsRequest?.inlinePresentationSpecs?.firstOrNull() ?: return
        val isInlineSupported = isInlineSuggestionSupported(inlinePresentationSpec)
        if (isInlineSupported) {
            val pendingIntent = PendingIntent.getService(
                context,
                0,
                Intent(),
                PendingIntent.FLAG_ONE_SHOT or
                    PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
            )
            val inlinePresentation = createInlinePresentation(
                context,
                pendingIntent,
                suggestionTitle,
                suggestionSubtitle,
                icon,
                inlinePresentationSpec,
            )
            this.setInlinePresentation(inlinePresentation)
        }
    }

    @SuppressLint("NewApi")
    private fun getMaxInlinedSuggestions(
        request: FillRequest,
    ): Int {
        if (appBuildConfig.sdkInt >= VERSION_CODES.R) {
            return request.inlineSuggestionsRequest?.maxSuggestionCount ?: 0
        }
        return 0
    }

    private fun autofillValue(
        credential: LoginCredentials?,
        autofillRequestedType: AutofillFieldType,
    ): AutofillValue? = if (autofillRequestedType == USERNAME) {
        AutofillValue.forText(credential?.username ?: "username")
    } else {
        AutofillValue.forText(credential?.password ?: "password")
    }

    @RequiresApi(VERSION_CODES.R)
    private fun createInlinePresentation(
        context: Context,
        pendingIntent: PendingIntent,
        suggestionTitle: String,
        suggestionSubtitle: String,
        icon: Int,
        inlinePresentationSpec: InlinePresentationSpec,
    ): InlinePresentation {
        val slice = createSlice(context, pendingIntent, suggestionTitle, suggestionSubtitle, icon)
        val inlinePresentation = InlinePresentation(slice, inlinePresentationSpec!!, false)
        return inlinePresentation
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

    private suspend fun loginCredentials(node: AutofillRootNode): List<LoginCredentials>? {
        var crendentials = node.website.takeUnless { it.isNullOrBlank() }?.let {
            autofillStore.getCredentials(it)
        }
        if (crendentials == null) {
            crendentials = node.packageId.takeUnless { it.isNullOrBlank() }?.let {
                autofillStore.getCredentials(it)
            }
        }
        return crendentials
    }

    @RequiresApi(VERSION_CODES.R)
    fun isInlineSuggestionSupported(inlinePresentationSpec: InlinePresentationSpec?): Boolean {
        // requires >= android 11
        return if (appBuildConfig.sdkInt >= VERSION_CODES.R && inlinePresentationSpec != null) {
            UiVersions.getVersions(inlinePresentationSpec.style).contains(UiVersions.INLINE_UI_VERSION_1)
        } else {
            false
        }
    }

    private fun createFormPresentation(
        context: Context,
        suggestionTitle: String,
        suggestionSubtitle: String,
        icon: Int,
    ) = buildAutofillRemoteViews(
        context = context,
        name = suggestionTitle,
        subtitle = suggestionSubtitle,
        iconRes = icon,
        shouldTintIcon = false,
    )

    private fun createAutofillSelectionIntent(
        context: Context,
        // framework: AutofillSelectionData.Framework,
        // type: AutofillSelectionData.Type,
        // uri: String?,
    ): PendingIntent {
        val intent = Intent(context, AutofillManagementActivity::class.java)
        return PendingIntent
            .getActivity(
                context,
                Random.nextInt(),
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
    }

    private fun buildAutofillRemoteViews(
        // autofillContentDescription: String?,
        context: Context,
        name: String,
        subtitle: String,
        @DrawableRes iconRes: Int,
        shouldTintIcon: Boolean,
    ): RemoteViews =
        RemoteViews(
            context.packageName,
            R.layout.autofill_remote_view,
        ).apply {
            /*autofillContentDescription?.let {
                setContentDescription(
                    R.id.container,
                    it,
                )
            }*/
            setTextViewText(
                R.id.title,
                name,
            )
            setTextViewText(
                R.id.subtitle,
                subtitle,
            )
            setImageViewResource(
                R.id.icon,
                iconRes,
            )
            /*setInt(
                R.id.container,
                "setBackgroundColor",
                Color.CYAN,
            )*/
            setInt(
                R.id.title,
                "setTextColor",
                Color.BLACK,
            )
            setInt(
                R.id.subtitle,
                "setTextColor",
                Color.BLACK,
            )
            if (shouldTintIcon) {
                setInt(
                    R.id.icon,
                    "setColorFilter",
                    Color.BLACK,
                )
            }
        }
}
