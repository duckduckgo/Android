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

package com.duckduckgo.lint.strings

import com.android.SdkConstants
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Node

@Suppress("UnstableApiUsage")
class MissingInstructionDetector : ResourceXmlDetector(), XmlScanner {

    private fun ignoreFile(context: Context) =
            ResourceUsageModel.isAnalyticsFile(context.file) ||
            !context.project.reportIssues


    override fun visitDocument(context: XmlContext, document: Document) {

        if (ignoreFile(context)) {
            return
        }

        val root = document.documentElement
        val translatable: Attr? = root?.getAttributeNode(SdkConstants.ATTR_TRANSLATABLE)
        if (translatable?.value != SdkConstants.VALUE_FALSE) {
            if (context.file.parentFile?.name == SdkConstants.FD_RES_VALUES) {
                visitNode(context, document)
            }
            return
        }
    }

    private fun isNodeNotTranslatable(node: Node) : Boolean {
        val translatable = node.parentNode.attributes.getNamedItem("translatable")
        val translatableValue = (translatable as Attr?)?.value
        return translatableValue != SdkConstants.VALUE_FALSE
    }

    private fun doesNodeContainPlaceholdersWithoutInstructionsOrParentInstructions(node: Node, parentHasInstruction: Boolean) : Boolean {
        val text = node.nodeValue
        val instruction = node.parentNode.attributes.getNamedItem("instruction")?.nodeValue
        return PLACEHOLDERS.firstOrNull { text.contains(it) } != null && (instruction.isNullOrEmpty() && !parentHasInstruction)
    }

    private fun visitNode(context: XmlContext, node: Node, parentHasInstruction: Boolean = false) {
        var parentInstruction = parentHasInstruction
        val nodeType = node.nodeType
        if (nodeType == Node.TEXT_NODE) {

            if (isNodeNotTranslatable(node) && doesNodeContainPlaceholdersWithoutInstructionsOrParentInstructions(node, parentHasInstruction)) {
                context.report(
                    MISSING_INSTRUCTION, node, location = context.getNameLocation(node),
                    "Missing instruction attribute or attribute empty"
                )
            }

        } else if (nodeType == Node.ELEMENT_NODE && node.nodeName == "plurals") {
            val instruction = node.attributes.getNamedItem("instruction")
            val translatable = node.attributes.getNamedItem("translatable")
            val translatableValue = (translatable as Attr?)?.value

            parentInstruction = (translatableValue != "false") && instruction != null
        }

        // Visit children
        val childNodes = node.childNodes
        var i = 0
        val n = childNodes.length
        while (i < n) {
            val child = childNodes.item(i)
            visitNode(context, child, parentInstruction)
            i++
        }
    }


    companion object {
        val PLACEHOLDERS = listOf(
            "%s", "%d", "%f", "%o", "%1${'$'}"
        )

        val MISSING_INSTRUCTION = Issue.create(
            id = "MissingInstruction",
            briefDescription = "Instruction missing in string or empty instruction",
            explanation = """
            A string that has placeholders and is translatable should include an instruction attribute""",
            category = CUSTOM_LINT_CHECKS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                MissingInstructionDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
