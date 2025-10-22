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
import org.jetbrains.uast.getParameterForArgument
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class DaxTextColorUsageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = DaxTextColorCallHandler(context)

    internal class DaxTextColorCallHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val methodName = node.methodName

            if (methodName == "DaxText") {
                checkColorParameter(node)
            }
        }

        private fun checkColorParameter(node: UCallExpression) {
            // Find the 'color' parameter
            val colorArgument = node.valueArguments.find { arg ->
                val parameterName = node.getParameterForArgument(arg)?.name
                parameterName == "color"
            } ?: return // No color parameter provided, using default is fine

            // Check if the color argument comes from DuckDuckGoTheme.textColors
            val isFromTextColors = isFromDuckDuckGoTextColors(colorArgument)

            if (!isFromTextColors) {
                reportInvalidColorUsage(colorArgument)
            }
        }

        private fun isFromDuckDuckGoTextColors(argument: org.jetbrains.uast.UExpression): Boolean {
            val source = argument.sourcePsi?.text ?: return false

            // Check if the source contains DuckDuckGoTheme.textColors or theme.textColors
            // This covers cases like:
            // - DuckDuckGoTheme.textColors.primary
            // - theme.textColors.secondary
            return source.contains("DuckDuckGoTheme.textColors") ||
                   source.contains("theme.textColors") ||
                   source.contains(".textColors.")
        }

        private fun reportInvalidColorUsage(colorArgument: org.jetbrains.uast.UExpression) {
            context.report(
                issue = INVALID_DAX_TEXT_COLOR_USAGE,
                location = context.getLocation(colorArgument),
                message = INVALID_DAX_TEXT_COLOR_USAGE.getExplanation(TextFormat.RAW)
            )
        }
    }

    companion object {
        val INVALID_DAX_TEXT_COLOR_USAGE = Issue
            .create(
                id = "InvalidDaxTextColorUsage",
                briefDescription = "DaxText color parameter should use DuckDuckGoTheme.textColors",
                explanation = """
                    Use DuckDuckGoTheme.textColors instead of arbitrary Color values to maintain design system consistency and theme support.

                    Examples:
                    • DuckDuckGoTheme.textColors.primary
                    • DuckDuckGoTheme.textColors.secondary

                    For one-off cases requiring custom colors, use good judgement or consider raising it in the Android Design System AOR.
                """.trimIndent(),
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 6,
                severity = Severity.WARNING,
                androidSpecific = true,
                implementation = Implementation(
                    DaxTextColorUsageDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
                )
            )
    }
}
