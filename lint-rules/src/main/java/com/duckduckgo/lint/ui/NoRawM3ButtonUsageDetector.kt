/*
 * Copyright (c) 2026 DuckDuckGo
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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class NoRawM3ButtonUsageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return RawM3ButtonCallHandler(context)
    }

    internal class RawM3ButtonCallHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            if (isInDesignSystemModule(context.project.name)) return

            val methodName = node.methodName ?: return
            if (methodName !in RAW_M3_BUTTON_NAMES) return

            val resolved = node.resolve() ?: return
            val qualifiedName = resolved.containingClass?.qualifiedName ?: return
            if (qualifiedName.startsWith("androidx.compose.material3")) {
                context.report(
                    issue = NO_RAW_M3_BUTTON_USAGE,
                    location = context.getLocation(node),
                    message = NO_RAW_M3_BUTTON_USAGE.getExplanation(TextFormat.RAW),
                )
            }
        }

        private fun isInDesignSystemModule(projectName: String): Boolean {
            return projectName.contains("design-system")
        }
    }

    companion object {
        private val RAW_M3_BUTTON_NAMES = setOf(
            "Button",
            "OutlinedButton",
            "TextButton",
            "IconButton",
            "FilledTonalButton",
            "ElevatedButton",
            "FilledTonalIconButton",
            "OutlinedIconButton",
            "FilledIconButton",
        )

        val NO_RAW_M3_BUTTON_USAGE = Issue
            .create(
                id = "NoRawM3ButtonUsage",
                briefDescription = "Use DaxButton variants instead of raw Material3 button composables",
                explanation = """
                    Use DuckDuckGo design system button components (DaxPrimaryButton, DaxSecondaryButton, \
                    DaxGhostButton, etc.) instead of raw Material3 Button, OutlinedButton, TextButton, or \
                    IconButton composables.

                    Raw M3 buttons bypass the design system's color tokens, ripple configuration, and sizing \
                    constraints. Using Dax* button variants ensures visual consistency across the app.
                """.trimIndent(),
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 6,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    NoRawM3ButtonUsageDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
