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
import android.os.Build
import android.os.Build.VERSION_CODES
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface AutofillParser {
    // Parses structure, detects autofill fields, and returns a list of root nodes.
    // Each root node contains a list of parsed autofill fields.
    // We intend that each root node has packageId and website, based on child values, but it's not guaranteed.
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
) : AutofillParser {

    private val classifier = ViewNodeClassifier()
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
        return autofillRootNodes
    }

    private fun traverseViewNode(
        viewNode: ViewNode,
    ): MutableList<ParsedAutofillField> {
        val autofillId = viewNode.autofillId ?: return mutableListOf()
        Timber.i("DDGAutofillService Parsing NODE: $autofillId")
        val traversalDataList = mutableListOf<ParsedAutofillField>()
        val packageId = viewNode.idPackage
        val website = viewNode.website()
        val autofillType = classifier.classify(viewNode)
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

    @SuppressLint("NewApi")
    private fun ViewNode.website(): String? {
        return this.webDomain
            .takeUnless { it?.isBlank() == true }
            ?.let { webDomain ->
                val webScheme = if (appBuildConfig.sdkInt >= Build.VERSION_CODES.P) {
                    this.webScheme.takeUnless { it.isNullOrBlank() }
                } else {
                    null
                } ?: "http"

                "$webScheme://$webDomain"
            }
    }
}

class ViewNodeClassifier {
    fun classify(viewNode: ViewNode): AutofillFieldType {
        val autofillId = viewNode.autofillId
        Timber.i("DDGAutofillService node $autofillId has autofillHints ${viewNode.autofillHints?.joinToString()}")
        Timber.i("DDGAutofillService node $autofillId has options ${viewNode.autofillOptions?.joinToString()}")
        Timber.i("DDGAutofillService node $autofillId has idEntry ${viewNode.idEntry}")
        Timber.i("DDGAutofillService node $autofillId has hints ${viewNode.hint}")
        Timber.i("DDGAutofillService node $autofillId is inputType ${viewNode.inputType and InputType.TYPE_CLASS_TEXT > 0}")
        Timber.i("DDGAutofillService node $autofillId has inputType ${viewNode.inputType}")
        Timber.i("DDGAutofillService node $autofillId has className ${viewNode.className}")
        Timber.i("DDGAutofillService node $autofillId has htmlInfo.attributes ${viewNode.htmlInfo?.attributes?.joinToString()}")

        var autofillType = getType(viewNode.autofillHints)
        if (autofillType == AutofillFieldType.UNKNOWN) {
            if (viewNode.inputType and InputType.TYPE_CLASS_TEXT > 0) {
                if (viewNode.idEntry?.containsAny(userNameKeywords) == true ||
                    viewNode.hint?.containsAny(userNameKeywords) == true
                ) {
                    autofillType = AutofillFieldType.USERNAME
                } else if (viewNode.idEntry?.containsAny(passwordKeywords) == true ||
                    viewNode.hint?.containsAny(userNameKeywords) == true
                ) {
                    autofillType = AutofillFieldType.PASSWORD
                }
            }
        }

        kotlin.runCatching { viewNode.autofillValue?.textValue?.toString() }.getOrElse {
            // If land here, it means we are not a text node, then assign Unknown type (logins are text fields)
            autofillType = AutofillFieldType.UNKNOWN
        }

        return autofillType
    }

    private fun getType(autofillHints: Array<String>?): AutofillFieldType {
        if (autofillHints == null) return AutofillFieldType.UNKNOWN
        if (autofillHints.any { it in USERNAME_HINTS }) return AutofillFieldType.USERNAME
        if (autofillHints.any { it in PASSWORD_HINTS }) return AutofillFieldType.PASSWORD
        return AutofillFieldType.UNKNOWN
    }

    private fun String.containsAny(words: List<String>): Boolean {
        return words.any { this.contains(it, ignoreCase = true) }
    }

    private val USERNAME_HINTS: List<String> = listOf(
        View.AUTOFILL_HINT_EMAIL_ADDRESS,
        View.AUTOFILL_HINT_USERNAME,
    )

    private val PASSWORD_HINTS: List<String> = listOf(
        View.AUTOFILL_HINT_PASSWORD,
        "passwordAuto",
    )

    private val userNameKeywords = listOf(
        "email",
        "username",
        "user name",
        "identifier",
        "account_name",
    )

    private val passwordKeywords = listOf(
        "password",
    )
}
