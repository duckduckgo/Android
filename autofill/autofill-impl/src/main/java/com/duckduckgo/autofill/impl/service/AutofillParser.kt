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

package com.duckduckgo.autofill.impl.service

import android.annotation.SuppressLint
import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.view.autofill.AutofillId
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.service.AutofillFieldType.UNKNOWN
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface AutofillParser {
    // Parses structure, detects autofill fields, and returns a list of root nodes.
    // Each root node contains a list of parsed autofill fields.
    // We intend that each root node has packageId and/or website, based on child values, but it's not guaranteed.
    fun parseStructure(structure: AssistStructure): MutableList<AutofillRootNode>
}

// Parsed root node of the autofill structure
data class AutofillRootNode(
    val packageId: String?,
    val website: String?,
    val parsedAutofillFields: List<ParsedAutofillField>, // Parsed fields in the structure
)

// Parsed autofill field
data class ParsedAutofillField(
    val autofillId: AutofillId,
    val packageId: String?,
    val website: String?,
    val value: String,
    val type: AutofillFieldType = AutofillFieldType.UNKNOWN,
    val originalNode: ViewNode,
)

enum class AutofillFieldType {
    USERNAME,
    PASSWORD,
    UNKNOWN,
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutofillParser @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val viewNodeClassifier: ViewNodeClassifier,
) : AutofillParser {

    override fun parseStructure(structure: AssistStructure): MutableList<AutofillRootNode> {
        val autofillRootNodes = mutableListOf<AutofillRootNode>()
        val windowNodeCount = structure.windowNodeCount
        Timber.i("DDGAutofillService windowNodeCount: $windowNodeCount")
        for (i in 0 until windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            windowNode.rootViewNode?.let { viewNode ->
                autofillRootNodes.add(
                    traverseViewNode(viewNode).convertIntoAutofillNode(),
                )
            }
        }
        Timber.i("DDGAutofillService convertedNodes: $autofillRootNodes")
        autofillRootNodes.forEach { node ->
            Timber.i("DDGAutofillService Detected Fields: ${node.parsedAutofillFields.filter { it.type != UNKNOWN }}")
        }
        return autofillRootNodes
    }

    private fun traverseViewNode(
        viewNode: ViewNode,
    ): MutableList<ParsedAutofillField> {
        val autofillId = viewNode.autofillId ?: return mutableListOf()
        Timber.i("DDGAutofillService Parsing NODE: $autofillId")
        val traversalDataList = mutableListOf<ParsedAutofillField>()
        val packageId = viewNode.validPackageId()
        val website = viewNode.website()
        val autofillType = viewNodeClassifier.classify(viewNode)
        val value = kotlin.runCatching { viewNode.autofillValue?.textValue?.toString() ?: "" }.getOrDefault("")
        val parsedAutofillField = ParsedAutofillField(
            autofillId = autofillId,
            packageId = packageId,
            website = website,
            value = value,
            type = autofillType,
            originalNode = viewNode,
        )
        Timber.i("DDGAutofillService Parsed as: $parsedAutofillField")
        traversalDataList.add(parsedAutofillField)

        for (i in 0 until viewNode.childCount) {
            val childNode = viewNode.getChildAt(i)
            traversalDataList.addAll(traverseViewNode(childNode))
        }

        return traversalDataList
    }

    private fun List<ParsedAutofillField>.convertIntoAutofillNode(): AutofillRootNode {
        return AutofillRootNode(
            packageId = this.firstOrNull { it.packageId != null }?.packageId,
            website = this.firstOrNull { it.website != null }?.website,
            parsedAutofillFields = this,
        )
    }

    private fun ViewNode.validPackageId(): String? {
        return this.idPackage
            .takeUnless { it.isNullOrBlank() }
            ?.takeUnless { it in INVALID_PACKAGE_ID }
    }

    @SuppressLint("NewApi")
    private fun ViewNode.website(): String? {
        return this.webDomain?.takeUnless { it.isBlank() }
            ?.let { nonEmptyDomain ->
                val scheme = if (appBuildConfig.sdkInt >= 28) {
                    this.webScheme.takeUnless { it.isNullOrBlank() } ?: "http"
                } else {
                    "http"
                }
                "$scheme://$nonEmptyDomain"
            }
    }

    companion object {
        private val INVALID_PACKAGE_ID = listOf("android")
    }
}
