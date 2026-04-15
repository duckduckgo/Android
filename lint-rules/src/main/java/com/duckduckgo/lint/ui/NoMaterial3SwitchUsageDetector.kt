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
class NoMaterial3SwitchUsageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = SwitchUsageHandler(context)

    internal class SwitchUsageHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            if (isInDesignSystemModule()) return

            val methodName = node.methodName ?: return
            if (methodName == "Switch") {
                val resolved = node.resolve() ?: return
                val qualifiedName = context.evaluator.getPackage(resolved)?.qualifiedName ?: return
                if (qualifiedName == MATERIAL3_PACKAGE) {
                    context.report(
                        issue = NO_MATERIAL3_SWITCH_USAGE,
                        location = context.getLocation(node),
                        message = NO_MATERIAL3_SWITCH_USAGE.getExplanation(TextFormat.RAW),
                    )
                }
            }
        }

        private fun isInDesignSystemModule(): Boolean {
            return context.project.name.contains("design-system")
        }
    }

    companion object {
        private const val MATERIAL3_PACKAGE = "androidx.compose.material3"

        val NO_MATERIAL3_SWITCH_USAGE = Issue
            .create(
                id = "NoMaterial3SwitchUsage",
                briefDescription = "Use DaxSwitch instead of Material3 Switch",
                explanation = "Use `DaxSwitch` from the design system instead of the Material3 `Switch` composable " +
                    "to ensure consistent styling across the app.",
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    NoMaterial3SwitchUsageDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
