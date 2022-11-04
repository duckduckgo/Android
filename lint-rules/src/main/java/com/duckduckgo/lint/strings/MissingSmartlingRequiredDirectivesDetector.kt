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

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.forEach
import org.w3c.dom.Document
import org.w3c.dom.Node

@Suppress("UnstableApiUsage")
class MissingSmartlingRequiredDirectivesDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    private fun ignoreFile(context: Context) =
        context.file.name.startsWith("donottranslate") || !context.file.name.contains("strings")

    override fun visitDocument(
        context: XmlContext,
        document: Document
    ) {
        if (ignoreFile(context)) {
            return
        }

        val discoveredDirectives = findDirectives(document)

        REQUIRED_SMARTLING_DIRECTIVES.forEach {
            if (!discoveredDirectives.contains(it)) {
                context.report(
                    Incident(
                        MISSING_SMARTLING_REQUIRED_DIRECTIVES,
                        "Missing directive: <!-- $it -->",
                        Location.create(context.file)
                    )
                )
            }
        }
    }

    private fun findDirectives(document: Document): Set<String> {
        val discoveredDirectives = mutableSetOf<String>()
        document.childNodes.forEach {
            if (it.nodeType == Node.COMMENT_NODE) {
                if (it.isSmartlingDirective()) discoveredDirectives.add(it.nodeValue.trim())
            } else if (it.nodeType == Node.ELEMENT_NODE) {
                if (it.isResourceNode()) return discoveredDirectives
            }
        }
        return discoveredDirectives
    }

    private fun Node.isSmartlingDirective(): Boolean = this.nodeValue.contains("smartling.")
    private fun Node.isResourceNode(): Boolean = this.nodeName == "resources"

    companion object {
        private val REQUIRED_SMARTLING_DIRECTIVES = listOf(
            "smartling.entity_escaping = false",
            "smartling.instruction_attributes = instruction"
        )

        val MISSING_SMARTLING_REQUIRED_DIRECTIVES = Issue.create(
            id = "MissingSmartlingRequiredDirectives",
            briefDescription = "Strings file has missing required Smartling directives.",
            explanation = """
                All strings files are required to have all the following directives specified before the <resources/> element:
                <!-- smartling.entity_escaping = false -->
                <!-- smartling.instruction_attributes = instruction -->
            """,
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 10,
            severity = ERROR,
            implementation = Implementation(
                MissingSmartlingRequiredDirectivesDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
