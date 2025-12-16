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
class DaxTextFieldTrailingIconDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = DaxTextFieldCallHandler(context)

    internal class DaxTextFieldCallHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val methodName = node.methodName

            if (methodName == "DaxTextField") {
                checkTrailingIconParameter(node)
            }
        }

        private fun checkTrailingIconParameter(node: UCallExpression) {
            // Find the 'trailingIcon' parameter
            val trailingIconArgument = node.valueArguments.find { arg ->
                val parameterName = node.getParameterForArgument(arg)?.name
                parameterName == "trailingIcon"
            } ?: return // No 'trailingIcon' parameter provided which is fine

            // Check if the trailingIcon uses an invalid composable
            if (isInvalidComposable(trailingIconArgument)) {
                reportInvalidComposableUsage(trailingIconArgument)
            }
        }

        private fun isInvalidComposable(argument: org.jetbrains.uast.UExpression): Boolean {
            val source = argument.sourcePsi?.text ?: return false

            // Only DaxTextFieldTrailingIcon should be used in the trailingIcon parameter
            // If the source doesn't contain it, then it's invalid
            return !source.contains("DaxTextFieldTrailingIcon")
        }

        private fun reportInvalidComposableUsage(arg: org.jetbrains.uast.UExpression) {
            context.report(
                issue = INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE,
                location = context.getLocation(arg),
                message = INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE.getExplanation(TextFormat.RAW),
            )
        }
    }

    companion object {
        val INVALID_DAX_TEXT_FIELD_TRAILING_ICON_USAGE = Issue
            .create(
                id = "InvalidDaxTextFieldTrailingIconUsage",
                briefDescription = "DaxTextField trailingIcon parameter should use DaxTextFieldTrailingIcon",
                explanation = """
                    Use DaxTextFieldTrailingIcon instead of arbitrary composables for the trailingIcon parameter to maintain design system consistency.

                    Example:
                    DaxTextField(
                        state = state,
                        trailingIcon = {
                            DaxTextFieldTrailingIcon(
                                painter = painterResource(R.drawable.ic_copy_24),
                                contentDescription = stringResource(R.string.icon_description)
                            )
                        }
                    )

                    This ensures consistent styling, spacing, and behavior across all text field icons in the app.
                """.trimIndent(),
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 6,
                severity = Severity.WARNING,
                androidSpecific = true,
                implementation = Implementation(
                    DaxTextFieldTrailingIconDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
