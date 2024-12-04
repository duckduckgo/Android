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
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES
import android.service.autofill.Dataset
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.view.autofill.AutofillValue
import androidx.annotation.RequiresApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
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
    private val viewProvider: AutofillServiceViewProvider,
    private val suggestionsFormatter: AutofillServiceSuggestionCredentialFormatter,
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
        Timber.i("DDGAutofillService Fillable Request for rootNode: $nodeToAutofill")
        val fillableFields = nodeToAutofill.parsedAutofillFields.filter { it.type != UNKNOWN }
        Timber.i("DDGAutofillService Fillable Request for fields: $fillableFields")

        val response = FillResponse.Builder()
        // We add credential suggestions
        fillableFields.forEach { fieldsToAutofill ->
            var inlineSuggestionsToShow = getMaxInlinedSuggestions(request) - RESERVED_SUGGESTIONS_SIZE
            val credentials = loginCredentials(nodeToAutofill)
            credentials?.forEach { credential ->
                val datasetBuilder = Dataset.Builder()
                Timber.i("DDGAutofillService suggesting credentials for: $fieldsToAutofill")
                val suggestionUISpecs = suggestionsFormatter.getSuggestionSpecs(credential)

                // >= android 11 inline presentations are supported
                if (appBuildConfig.sdkInt >= VERSION_CODES.R && inlineSuggestionsToShow > 0) {
                    datasetBuilder.addInlinePresentationsIfSupported(
                        context,
                        request,
                        suggestionUISpecs.title,
                        suggestionUISpecs.subtitle,
                        suggestionUISpecs.icon,
                    )
                    inlineSuggestionsToShow -= 1
                }
                val formPresentation = viewProvider.createFormPresentation(
                    context,
                    suggestionUISpecs.title,
                    suggestionUISpecs.subtitle,
                    suggestionUISpecs.icon,
                )
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
        val specs = suggestionsFormatter.getOpenDuckDuckGoSuggestionSpecs()
        val pendingIntent = createAutofillSelectionIntent(context)
        if (appBuildConfig.sdkInt >= VERSION_CODES.R) {
            ddgAppDataSet.addInlinePresentationsIfSupported(context, request, specs.title, specs.subtitle, specs.icon)
        }
        val formPresentation = viewProvider.createFormPresentation(context, specs.title, specs.subtitle, specs.icon)
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
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            Intent(),
            PendingIntent.FLAG_ONE_SHOT or
                PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )
        viewProvider.createInlinePresentation(
            context,
            pendingIntent,
            suggestionTitle,
            suggestionSubtitle,
            icon,
            inlinePresentationSpec,
        )?.let { inlinePresentation ->
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

    private suspend fun loginCredentials(node: AutofillRootNode): List<LoginCredentials>? {
        val crendentialsForDomain = node.website.takeUnless { it.isNullOrBlank() }?.let {
            autofillStore.getCredentials(it)
        } ?: emptyList()

        val crendentialsForPackage = node.packageId.takeUnless { it.isNullOrBlank() }?.let {
            autofillStore.getCredentials(it)
        } ?: emptyList()

        Timber.i("DDGAutofillService credentials for domain: $crendentialsForDomain")
        Timber.i("DDGAutofillService credentials for package: $crendentialsForPackage")
        return crendentialsForDomain.plus(crendentialsForPackage).distinct()
    }

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
}
