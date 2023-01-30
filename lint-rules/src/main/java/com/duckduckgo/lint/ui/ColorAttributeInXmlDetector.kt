/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.lint.ui

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr

/**
 * Prohibits use of hardcoded colors in XML layouts and color resources.
 *
 * A hardcoded color includes:
 *
 * - a reference to a color resource which doesn't include "dax" in the name.
 *   "dax" is used as an allowlist filter, where we'll assume that the resource contains theme-friendly colors
 *   or it's an exception to the rule.
 * - a color hexcode
 *
 */
internal class ColorAttributeInXmlDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType) =
        folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES || folderType == ResourceFolderType.COLOR

    override fun getApplicableAttributes(): Collection<String> = XmlScannerConstants.ALL

    override fun visitAttribute(
        context: XmlContext,
        attribute: Attr
    ) {
        when (context.resourceFolderType) {
            ResourceFolderType.COLOR, ResourceFolderType.LAYOUT -> if (attribute.value.isColorHexcode() || attribute.value.isHardcodedColorResInXml()) {
                reportIssue(context, attribute)
            }

            ResourceFolderType.VALUES -> {
                // we're only interested in style resources
                val item = if (attribute.belongsToItem()) attribute.ownerElement else return
                if (item.belongsToThemeOrThemeOverlay()) {
                    // we define attributes in themes/theme overlays so it's normal to have hardcoded colors here
                    // kind of, maybe
                    return
                }
                if (item.belongsToStyle()) {
                    val value = item.firstChild.nodeValue
                    if (value.isColorHexcode() || value.isHardcodedColorResInXml()) {
                        reportIssue(context, attribute)
                    }
                }
            }

            else -> return
        }
    }

    private fun reportIssue(
        context: XmlContext,
        attribute: Attr
    ) {
        context.report(
            issue = INVALID_COLOR_ATTRIBUTE,
            location = context.getNameLocation(attribute),
            message = INVALID_COLOR_ATTRIBUTE.getExplanation(TextFormat.RAW),
        )
    }

    companion object {
        val INVALID_COLOR_ATTRIBUTE = Issue
            .create(
                id = "InvalidColorAttribute",
                briefDescription = "@colors are not allowed, used ?attr/daxColor instead",
                explanation = "@colors are not allowed, used ?attr/daxColor instead",
                moreInfo = "https://app.asana.com/0/1202857801505092/1202928695963077",
                category = Category.CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    ColorAttributeInXmlDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE,
                ),
            )
    }
}
