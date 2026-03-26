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

package com.duckduckgo.lint.ui

import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class NoComposeViewUsageDetector : LayoutDetector(), SourceCodeScanner {

    // XML Detection
    override fun getApplicableElements() = listOf("androidx.compose.ui.platform.ComposeView")

    override fun visitElement(context: XmlContext, element: Element) {
        if (isInDesignSystemModule(context.project.name)) return
        reportComposeViewUsageInXml(context, element)
    }

    // Kotlin Code Detection
    override fun getApplicableConstructorTypes() = listOf("androidx.compose.ui.platform.ComposeView")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (isInDesignSystemModule(context.project.name)) return
        reportComposeViewUsageInCode(context, node)
    }

    private fun reportComposeViewUsageInXml(context: XmlContext, element: Element) {
        context.report(
            issue = NO_COMPOSE_VIEW_USAGE,
            location = context.getNameLocation(element),
            message = NO_COMPOSE_VIEW_USAGE.getExplanation(TextFormat.RAW)
        )
    }

    private fun reportComposeViewUsageInCode(context: JavaContext, node: UCallExpression) {
        context.report(
            issue = NO_COMPOSE_VIEW_USAGE,
            location = context.getLocation(node),
            message = NO_COMPOSE_VIEW_USAGE.getExplanation(TextFormat.RAW)
        )
    }

    private fun isInDesignSystemModule(projectName: String): Boolean {
        return projectName.contains("design-system")
    }

    companion object {
        val NO_COMPOSE_VIEW_USAGE = Issue
            .create(
                id = "NoComposeViewUsage",
                briefDescription = "ComposeView should not be used in XML layouts or custom views",
                explanation = "Compose is not yet approved to be used in production. ComposeView should not be used in XML layouts or custom views until Compose usage is officially approved for the codebase.",
                moreInfo = "https://app.asana.com/0/1202857801505092/list",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    NoComposeViewUsageDetector::class.java,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE, Scope.TEST_SOURCES)
                )
            )
    }
}
