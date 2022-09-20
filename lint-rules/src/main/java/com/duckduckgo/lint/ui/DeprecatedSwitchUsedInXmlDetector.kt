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

package com.duckduckgo.lint.ui

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

@Suppress("UnstableApiUsage")
class DeprecatedSwitchUsedInXmlDetector : LayoutDetector() {

    override fun getApplicableElements() = SWITCHES

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun visitElement(
        context: XmlContext,
        element: Element
    ) {
        reportUsage(context, element)
    }

    private fun reportUsage(
        context: XmlContext,
        element: Element
    ) {
        context.report(
            issue = DEPRECATED_SWITCH_IN_XML,
            location = context.getNameLocation(element),
            message = DEPRECATED_SWITCH_IN_XML.getExplanation(TextFormat.RAW)
        )
    }

    companion object {

        private const val APP_COMPAT_SWITCH = "androidx.appcompat.widget.SwitchCompat"
        private const val MATERIAL_SWITCH = "com.google.android.material.switchmaterial.SwitchMaterial"
        private const val SWITCH = "Switch"

        val SWITCHES = listOf(APP_COMPAT_SWITCH, MATERIAL_SWITCH, SWITCH)
        val DEPRECATED_SWITCH_IN_XML = Issue
            .create(
                id = "AndroidSwitchInXml",
                briefDescription = "Default Android Switch used instead of Design System Component",
                explanation = "Always favor the use of the Design System Component SwitchView",
                moreInfo = "https://app.asana.com/0/1202857801505092/list",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    DeprecatedSwitchUsedInXmlDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
    }
}
