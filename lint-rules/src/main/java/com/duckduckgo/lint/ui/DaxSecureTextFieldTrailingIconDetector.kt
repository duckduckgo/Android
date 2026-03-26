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
class DaxSecureTextFieldTrailingIconDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = DaxSecureTextFieldCallHandler(context)

    internal class DaxSecureTextFieldCallHandler(private val context: JavaContext) : UElementHandler() {

        private val validTrailingIconMembers: List<String> by lazy {
            getTrailingIconScopeMembers()
        }

        override fun visitCallExpression(node: UCallExpression) {
            val methodName = node.methodName

            if (methodName == "DaxSecureTextField") {
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

        private fun getTrailingIconScopeMembers(): List<String> {
            val scopeClass = context.evaluator.findClass(TRAILING_ICON_SCOPE_CLASS)
                ?: return emptyList()

            return scopeClass.methods
                .filter { !it.isConstructor }
                .mapNotNull { it.name }
        }

        private fun isInvalidComposable(argument: org.jetbrains.uast.UExpression): Boolean {
            val source = argument.sourcePsi?.text ?: return false

            // Check if the source contains any of the valid trailing icon members
            // If the scope class couldn't be resolved, fall back to allowing the usage
            if (validTrailingIconMembers.isEmpty()) return false

            return validTrailingIconMembers.none { member -> source.contains(member) }
        }

        private fun reportInvalidComposableUsage(arg: org.jetbrains.uast.UExpression) {
            context.report(
                issue = INVALID_DAX_SECURE_TEXT_FIELD_TRAILING_ICON_USAGE,
                location = context.getLocation(arg),
                message = INVALID_DAX_SECURE_TEXT_FIELD_TRAILING_ICON_USAGE.getExplanation(TextFormat.RAW),
            )
        }
    }

    companion object {
        private const val TRAILING_ICON_SCOPE_CLASS =
            "com.duckduckgo.common.ui.compose.textfield.DaxTextFieldTrailingIconScope"

        val INVALID_DAX_SECURE_TEXT_FIELD_TRAILING_ICON_USAGE = Issue
            .create(
                id = "InvalidDaxSecureTextFieldTrailingIconUsage",
                briefDescription = "DaxSecureTextField trailingIcon parameter should only use composables from DaxTextFieldTrailingIconScope",
                explanation = """
                    Use composables from DaxTextFieldTrailingIconScope instead of arbitrary composables 
                    for the trailingIcon parameter to maintain design system consistency.

                    Example:
                    DaxSecureTextField(
                        state = state,
                        isPasswordVisible = isPasswordVisible,
                        onShowHidePasswordIconClick = { /* toggle visibility */ },
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
                    DaxSecureTextFieldTrailingIconDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
