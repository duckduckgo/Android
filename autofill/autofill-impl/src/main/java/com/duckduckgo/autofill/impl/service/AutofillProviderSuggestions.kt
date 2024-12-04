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
        nodeToAutofill: AutofillRootNode,
        request: FillRequest,
    ): FillResponse
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutofillProviderSuggestions @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val autofillStore: AutofillStore,
) : AutofillProviderSuggestions {

    companion object {
        private const val RESERVED_SUGGESTIONS_SIZE = 1
    }

    @SuppressLint("NewApi")
    override suspend fun buildSuggestionsResponse(
        context: Context,
        nodeToAutofill: AutofillRootNode,
        request: FillRequest,
    ): FillResponse {
        var inlineSuggestionsToShow = getMaxInlinedSuggestions(request) - RESERVED_SUGGESTIONS_SIZE
        Timber.i("DDGAutofillService Fillable Request for rootNode: $nodeToAutofill")
        val fillableFields = nodeToAutofill.parsedAutofillFields.filter { it.type != UNKNOWN }
        Timber.i("DDGAutofillService Fillable Request for fields: $fillableFields")

        val response = FillResponse.Builder()
        // We add credential suggestions
        fillableFields.forEach { fieldsToAutofill ->
            val credentials = loginCredentials(nodeToAutofill)
            credentials?.forEach { credential ->
                val datasetBuilder = Dataset.Builder()
                Timber.i("DDGAutofillService suggesting credentials for: $fieldsToAutofill")
                val suggestionTitle = credential.username.orEmpty()
                val suggestionSubtitle = credential.domain.takeUnless {
                    it.isNullOrBlank()
                } ?: run {
                    credential.domainTitle.takeUnless { it.isNullOrBlank() }
                } ?: ""
                val icon = R.drawable.ic_dax_silhouette_primary_24
                // >= android 11 inline presentations are supported
                if (appBuildConfig.sdkInt >= VERSION_CODES.R && inlineSuggestionsToShow > 0) {
                    datasetBuilder.addInlinePresentationsIfSupported(context, request, suggestionTitle, suggestionSubtitle, icon)
                    inlineSuggestionsToShow -= 1
                }
                val formPresentation = createFormPresentation(context, suggestionTitle, suggestionSubtitle, icon)
                datasetBuilder.setValue(
                    fieldsToAutofill.autofillId,
                    autofillValue(credential, fieldsToAutofill.type),
                    formPresentation,
                )
                val dataset = datasetBuilder.build()
                response.addDataset(dataset)
            }
        }

        // Last suggestion to open DDG App and manually choose a credential
        val ddgAppDataSetBuild = createAccessDDGDataSet(context, request, fillableFields)
        response.addDataset(ddgAppDataSetBuild)

        // TODO: add ignoredAutofillIds https://app.asana.com/0/1200156640058969/1209226370597334/f
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
        val suggestionTitle = context.getString(R.string.autofill_service_suggestion_search_passwords)
        val suggestionSubtitle = ""
        val icon = R.drawable.ic_dax_silhouette_primary_24
        val pendingIntent = createAutofillSelectionIntent(context)
        if (appBuildConfig.sdkInt >= VERSION_CODES.R) {
            ddgAppDataSet.addInlinePresentationsIfSupported(context, request, suggestionTitle, suggestionSubtitle, icon)
        }
        val formPresentation = createFormPresentation(context, suggestionTitle, suggestionSubtitle, icon)
        // suggestions will only appear when field focused, safe to add suggestions to all fillableFields
        fillableFields.forEach { fieldsToAutofill ->
            ddgAppDataSet.setValue(
                fieldsToAutofill.autofillId,
                AutofillValue.forText("placeholder"),
                formPresentation,
            )
        }
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
        val inlinePresentation = InlinePresentation(slice, inlinePresentationSpec, false)
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
    )

    private fun createAutofillSelectionIntent(context: Context): PendingIntent {
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
