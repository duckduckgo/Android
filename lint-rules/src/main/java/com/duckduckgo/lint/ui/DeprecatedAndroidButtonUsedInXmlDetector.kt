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
class DeprecatedAndroidButtonUsedInXmlDetector : LayoutDetector() {

    override fun getApplicableElements() = BUTTON_WIDGETS

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
            issue = DEPRECATED_BUTTON_IN_XML,
            location = context.getNameLocation(element),
            message = DEPRECATED_BUTTON_IN_XML.getExplanation(TextFormat.RAW)
        )
    }

    companion object {

        private const val LEGACY_PRIMARY_LOWERCASE_BUTTON = "com.duckduckgo.mobile.android.ui.view.button.ButtonPrimaryLowercase"
        private const val LEGACY_PRIMARY_ROUNDED_BUTTON = "com.duckduckgo.mobile.android.ui.view.button.ButtonPrimaryRounded"
        private const val LEGACY_SECONDARY_ROUNDED_BUTTON = "com.duckduckgo.mobile.android.ui.view.button.ButtonSecondaryRounded"

        private const val LEGACY_ANDROID_BUTTON = "Button"
        private const val MATERIAL_BUTTON = "com.google.android.material.button.MaterialButton"

        // this list will also contain the depreacated DS buttons that we are using until the migration is complete
        val BUTTON_WIDGETS = listOf(LEGACY_ANDROID_BUTTON, MATERIAL_BUTTON)
        val DEPRECATED_BUTTON_IN_XML = Issue
            .create(
                id = "AndroidButtonInXml",
                briefDescription = "Default Android Button Widget used instead of Design System Component",
                explanation = "Always favor the use of the Design System Component. ButtonPrimaryLarge, ButtonSecondaryLarge, etc...",
                moreInfo = "https://app.asana.com/0/1202857801505092/list",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    DeprecatedAndroidButtonUsedInXmlDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
    }
}
